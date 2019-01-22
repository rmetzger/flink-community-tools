package de.robertmetzger.flink.community.flinkbot;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;

import java.io.IOException;

import java.util.*;

public class Flinkbot {

    private static final String BOT_NAME = "@flinkbot";
    private static final String TRACKING_MESSAGE = "Thanks a lot for your contribution to the Apache Flink project. I'm the "+BOT_NAME+". I help the community\n" +
            "to review your pull request. We will use this comment to track the progress of the review.\n" +
            "\n" +
            "\n" +
            "## Review Progress\n" +
            "\n" +
            "* [ ] 1. The [contribution] is well-described.\n" +
            "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
            "* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
            "* [ ] 4. The [architecture] is sound.\n" +
            "* [ ] 5. Overall code [quality] is good.\n" +
            "\n" +
            "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

    private static final String[] VALID_APPROVALS = {"contribution", "consensus", "architecture", "quality"};

    private final Github gh;

    public Flinkbot(Github gh) {
        this.gh = gh;
        if(!("@"+gh.getBotName()).equals(BOT_NAME)) {
            throw new RuntimeException("Something is wrong");
        }
    }

    /**
     * Check if there are new pull requests w/o a managed comment yet.
     *  Create comment
     */
    public void checkForNewPRs() {
        List<GHIssue> prs = gh.getAllPullRequests();
        // remove all PRs we've commented on already
        prs.removeIf(pr -> {
            System.out.println("Checking PR " + pullToSimpleString(pr));
            return pullRequestHasComment(pr);
        });

        // put comment
        for (GHIssue pr : prs) {
            System.out.println("Commenting on PR " + pullToSimpleString(pr));
            try {
                pr.comment(TRACKING_MESSAGE);
            } catch (IOException e) {
                throw new RuntimeException("err", e);
            }
        }
        System.out.println("Done checking for new PRs. Requests remaining: " + gh.getRemainingRequests());
    }
    private boolean pullRequestHasComment(GHIssue pr) {
        try {
            return pr.getComments().stream().anyMatch(comment -> {
                // call getUserName() to avoid an additional API request
                if (comment.getUserName().equals(gh.getBotName())) {
                    // check if message is the same.
                    String body = comment.getBody();
                    return isTrackingMessage(body);
                }
                return false;
            });
        } catch (IOException e) {
            // fail
            e.printStackTrace();
            throw new RuntimeException("err", e);
        }
    }

    private boolean isTrackingMessage(String body) {
        return body.substring(0, Math.min(body.length(), 10)).equals(TRACKING_MESSAGE.substring(0, 10));
    }

    public void checkForNewActions() {
        // get notifications
        List<Github.NotificationAndComments> notifications = gh.getNewNotifications();
        for(Github.NotificationAndComments notification: notifications) {
            processBotMentions(notification.getComments());
            try {
                notification.getNotification().markAsRead();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method reads all comments of a PR, locates the comment that is maintained by the bot,
     * and all mentions
     *
     * @param comments all comments of a PR
     */
    /*private*/ void processBotMentions(List<GHIssueComment> comments) {
        if(comments == null) {
            return;
        }
        GHIssueComment trackingComment = null;
        Map<String, List<String>> approvals = new HashMap<>();
        List<String> attention = new ArrayList<>();
        for(GHIssueComment comment: comments) {
            if(isTrackingMessage(comment.getBody())) {
                trackingComment = comment;
            }

            try {
                String[] commentLines = comment.getBody().split("\n");
                for(String line: commentLines) {
                    if(line.contains(BOT_NAME)) {
                        String[] tokens = line.split(" ");
                        for(int i = 0; i < tokens.length; i++) {
                            if(tokens[i].equals(BOT_NAME)) {
                                if(i+2 >= tokens.length) {
                                    System.out.println("Incomplete command in: " + line);
                                    break; // stop processing this line
                                }
                                String action = tokens[i+1].toLowerCase();
                                String approval = tokens[i+2].toLowerCase();
                                if(action.equals("attention")) {
                                    if(approval.substring(0,1).equals("@")) {
                                        attention.add(approval);
                                    }
                                    // look for more names
                                    for(int j = i + 2; j < tokens.length; j++) {
                                        if(tokens[j].substring(0,1).equals("@")) {
                                            attention.add(tokens[j]);
                                        }
                                    }
                                } else if(action.equals("approve") || action.equals("disapprove")) {
                                    if(!ArrayUtils.contains(VALID_APPROVALS, approval)) {
                                        System.out.println("Invalid approval/aspect in " + line);
                                        break;
                                    }
                                    if(action.equals("approve")) {
                                        List<String> approver = approvals.get(approval);
                                        if(approver == null) {
                                            approver = new ArrayList<>();
                                        }
                                        approver.add("@"+comment.getUserName());
                                        approvals.put(approval, approver);
                                    }
                                    if(action.equals("disapprove")) {
                                        List<String> approver = approvals.get(approval);
                                        if(approver == null) {
                                            approver = new ArrayList<>();
                                        }
                                        approver.remove("@"+comment.getUserName());
                                        approvals.put(approval, approver);
                                    }
                                } else {
                                    System.out.println("Incomplete command in: " + line);
                                    break; // stop processing this line
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println("Error processing comment '"+comment.getBody()+"'. Msg: "+t.getMessage());
                t.printStackTrace();
            }

        }

        // update tracking comment
        if(trackingComment == null) {
            System.out.println("Invalid notification? comments do not contain tracking message " + comments);
        } else {
            // generate comment
            StringBuffer newComment = new StringBuffer();
            String[] messageLines = TRACKING_MESSAGE.split("\n");
            for(String line: messageLines) {
                // first, we always copy the original line
                newComment.append(line);
                newComment.append("\n");
                // then, we decide whether we add something
                for(String approval: VALID_APPROVALS) {
                    if(line.contains("[" + approval + "]")) {
                       String append = "    - Approved by ";
                       List<String> approvers = approvals.get(approval);
                       if(approvers != null) {
                           append += StringUtils.join(approvers, ", ");
                           newComment.append(append);
                           newComment.append("\n");
                       }
                    }
                }
                if(line.contains("attention")) {
                    String append = "    - Needs attention by ";
                    if(attention.size() > 0) {
                        append += StringUtils.join(attention, ", ");
                        newComment.append(append);
                        newComment.append("\n");
                    }
                }
            }
            try {
                newComment.deleteCharAt(newComment.length()-1); // remove trailing newline
                String newCommentString = newComment.toString();
                if(!newCommentString.equals(trackingComment.getBody())) {
                    // need to update
                    trackingComment.update(newCommentString);
                }
            } catch (IOException e) {
                throw new RuntimeException("Err",e);
            }
        }
    }

    private static String pullToSimpleString(GHIssue pr) {
        return "#" + pr.getNumber() + ": " + pr.getTitle();
    }
}
