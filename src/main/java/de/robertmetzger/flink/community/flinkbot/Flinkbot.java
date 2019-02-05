package de.robertmetzger.flink.community.flinkbot;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.util.*;
import java.util.stream.Collectors;

public class Flinkbot {
    private static Logger LOG = LoggerFactory.getLogger(Flinkbot.class);

    private final String botName;
    private final String trackingMessage;

    private static final String[] VALID_APPROVALS = {"description", "consensus", "architecture", "quality"};

    private final Github gh;
    private final String[] committers;
    private final String[] pmc;


    public Flinkbot(Github gh, String[] committers, String[] pmc) {
        this.gh = gh;
        this.botName = "@"+gh.getBotName();
        this.trackingMessage = "Thanks a lot for your contribution to the Apache Flink project. I'm the "+ botName +". I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ❌ 1. The [description] looks good.\n" +
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❔ 3. Needs [attention] from.\n" +
                "* ❌ 4. The change fits into the overall [architecture].\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The "+ botName +" bot supports the following commands:\n" +
                "\n" +
                " - `"+ botName +" approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `"+ botName +" approve all` to approve all aspects\n" +
                " - `"+ botName +" attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `"+ botName +" disapprove architecture` to remove an approval\n" +
                "</details>";

        this.committers = committers;
        this.pmc = pmc;
    }

    /**
     * Check if there are new pull requests w/o a managed comment yet.
     *  Create comment
     *
     * The method is synchronized, to avoid multiple threads concurrently processing new PRs.
     */
    public synchronized void checkForNewPRs() {
        List<GHIssue> prs = gh.getAllPullRequests();
        // remove all PRs we've commented on already
        prs.removeIf(pr -> {
            LOG.debug("Checking PR " + pullToSimpleString(pr));
            return pullRequestHasComment(pr);
        });

        // put comment
        for (GHIssue pr : prs) {
            LOG.info("Commenting with tracking message on PR " + pullToSimpleString(pr));
            try {
                pr.comment(trackingMessage);
            } catch (IOException e) {
                LOG.warn("Error writing tracking message", e);
            }
        }
        LOG.info("Done checking for new PRs. Requests remaining: " + gh.getRemainingRequests());
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
            LOG.warn("Error checking for comment", e);
            return false;
        }
    }

    private boolean isTrackingMessage(String body) {
        return body.substring(0, Math.min(body.length(), 10)).equals(trackingMessage.substring(0, 10));
    }

    /**
     * This is processing all incoming mentions
     *
     * @param notifications new incoming notifications
     */
    public void processBotMentions(Iterator<GHThread> notifications) {
        while (notifications.hasNext()) {
            GHThread thread = notifications.next();
            LOG.info("Found a notification with title '" + thread.getTitle() + "': " + thread);
            if (thread.getReason().equals("mention")) {
                try {
                    // we immediately mark the notification as read to avoid concurrency issues with newer comments
                    // being posted while still processing the old ones.
                    thread.markAsRead();
                    Thread.sleep(1000); // + sleep some time to ensure we get new comments with this fetch

                    List<GHIssueComment> comments = thread.getBoundPullRequest().getComments();
                    // sort by date, so that we process mentions in order
                    Collections.sort(comments, (o1, o2) -> {
                        try {
                            return o1.getCreatedAt().compareTo(o2.getCreatedAt());
                        } catch (IOException e) {
                            // Throw an exception here. IOExceptions should not happen (It's a mistake by the library)
                            LOG.warn("Error while sorting", e);
                            throw new RuntimeException("Error while sorting", e);
                        }
                    });
                    //
                    updatePullRequestThread(comments);
                } catch (Throwable e) {
                    LOG.warn("Error while processing notification", e);
                }
            }
            LOG.info("Done processing notification. Requests remaining: " + gh.getRemainingRequests());
        }
    }


    /*private */ void updatePullRequestThread(List<GHIssueComment> comments) {
        if(comments == null) {
            LOG.warn("Notification without comments");
            return;
        }
        LOG.debug("Processing pull request thread with " + comments.size() + " comments");
        GHIssueComment trackingComment = null;
        Map<String, Set<String>> approvals = new HashMap<>();
        Set<String> attention = new HashSet<>();
        for(GHIssueComment comment: comments) {
            if(isTrackingMessage(comment.getBody())) {
                trackingComment = comment;
            }

            try {
                String[] commentLines = comment.getBody().split("\n");
                for(String line: commentLines) {
                    if(line.contains(botName)) {
                        String[] tokens = line.split(" ");
                        for(int i = 0; i < tokens.length; i++) {
                            if(tokens[i].equals(botName)) {
                                if(i+2 >= tokens.length) {
                                    LOG.debug("Incomplete command in: " + line);
                                    break; // stop processing this line
                                }
                                String action = tokens[i+1].toLowerCase().trim();
                                String approval = tokens[i+2].toLowerCase().trim();
                                if(action.equals("attention")) {
                                    if(approval.substring(0,1).equals("@")) {
                                        attention.add(approval.trim());
                                    }
                                    // look for more names
                                    for(int j = i + 3; j < tokens.length; j++) {
                                        if(tokens[j].substring(0,1).equals("@")) {
                                            attention.add(tokens[j].trim());
                                        }
                                    }
                                } else if(action.equals("approve") || action.equals("disapprove")) {
                                    if(!ArrayUtils.contains(VALID_APPROVALS, approval) && !approval.equals("all")) {
                                        LOG.debug("Invalid approval/aspect in " + line);
                                        break;
                                    }
                                    if(action.equals("approve")) {
                                        if(approval.equals("all")) {
                                            for(String validApproval: VALID_APPROVALS) {
                                                addApproval(approvals, validApproval, comment.getUserName());
                                            }
                                        } else {
                                            addApproval(approvals, approval, comment.getUserName());
                                        }
                                    }
                                    if(action.equals("disapprove")) {
                                        Set<String> approver = approvals.get(approval);
                                        if(approver == null) {
                                            approver = new HashSet<>();
                                        }
                                        approver.remove("@"+comment.getUserName());
                                        approvals.put(approval, approver);
                                    }
                                } else {
                                    LOG.debug("Incomplete command in: " + line);
                                    break; // stop processing this line
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOG.warn("Error processing comment '"+comment.getBody()+"'. Msg: "+t.getMessage(), t);
            }

        }

        // update tracking comment
        if(trackingComment == null) {
            LOG.warn("Invalid notification? comments do not contain tracking message " + comments);
        } else {
            // generate comment
            StringBuffer newComment = new StringBuffer();
            String[] messageLines = trackingMessage.split("\n");
            for(String line: messageLines) {
                // decide whether we add something
                boolean tick = false;
                boolean attentionTick = false;
                String nextLine = null;
                for(String approval: VALID_APPROVALS) {
                    if(line.contains("[" + approval + "]")) {
                       String append = "    - Approved by ";
                       Set<String> approversSet = approvals.get(approval);
                       if(approversSet != null && approversSet.size() > 0) {
                           List<String> approvers = new ArrayList<>(approversSet);
                           Collections.sort(approvers); // equality
                           append += StringUtils.join(addCommunityStatus(approvers), ", ");
                           nextLine = append;
                           tick = true;
                       }
                    }
                }
                if(line.contains("[attention]")) {
                    String append = "    - Needs attention by ";
                    if(attention.size() > 0) {
                        List<String> attSorted = new ArrayList<>(attention);
                        Collections.sort(attSorted);
                        append += StringUtils.join(addCommunityStatus(attSorted), ", ");
                        nextLine = append;
                        tick = true;
                        attentionTick = true;
                    }
                }
                // copy the original line
                if(tick) {
                    if (attentionTick) {
                        newComment.append(line.replace("❔", "❗"));
                    } else {
                        newComment.append(line.replace("❌", "✅"));
                    }
                } else {
                    newComment.append(line);
                }

                newComment.append("\n");
                if(nextLine != null) {
                    newComment.append(nextLine);
                    newComment.append("\n");
                }
            }
            try {
                newComment.deleteCharAt(newComment.length()-1); // remove trailing newline
                String newCommentString = newComment.toString();
                if(!newCommentString.equals(trackingComment.getBody())) {
                    // LOG.debug("new '{}' old '{}'", newCommentString.length(), trackingComment.getBody().length());
                    // LOG.debug("new '{}' old '{}'", newCommentString, trackingComment.getBody());

                    // need to update
                    trackingComment.update(newCommentString);
                    LOG.info("Updating tracking comment on PR: " + pullToSimpleString(trackingComment.getParent()));
                }
            } catch (IOException e) {
                LOG.warn("Error updating tracking comment", e);
            }
        }
    }

    private List<String> addCommunityStatus(List<String> ghLogins) {
        return ghLogins.stream().map(login -> {
            String noAt = login.replace("@", "");
            if(ArrayUtils.contains(this.committers, noAt)) {
                login += " [committer]";
            } else if(ArrayUtils.contains(this.pmc, noAt)) {
                login += " [PMC]";
            }
            return login;
        }).collect(Collectors.toList());
    }

    private static void addApproval(Map<String, Set<String>> approvals, String approvalName, String userName) {
        Set<String> approver = approvals.get(approvalName);
        if (approver == null) {
            approver = new HashSet<>();
        }
        approver.add("@" + userName);
        approvals.put(approvalName, approver);
    }
    private static String pullToSimpleString(GHIssue pr) {
        return "#" + pr.getNumber() + ": " + pr.getTitle();
    }
}
