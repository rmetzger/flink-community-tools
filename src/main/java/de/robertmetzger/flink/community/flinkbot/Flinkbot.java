package de.robertmetzger.flink.community.flinkbot;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Flinkbot {
    private static Logger LOG = LoggerFactory.getLogger(Flinkbot.class);

    private final String botName;
    private final String trackingMessage;

    // order matters
    private static final String[] VALID_APPROVALS = {"description", "consensus", "architecture", "quality"};

    private static final Pattern GET_SHA_PATTERN = Pattern.compile(".* ([a-z0-9]{40}) \\(.*\\)");

    private static final PullRequestCheck[] PULL_REQUEST_CHECK = {
        new PomChangesCheck(), new DocumentationCheck(), new AssignedJiraCheck()
    };

    private static final String LABEL_PREFIX = "review=";
    private static final String LABEL_COLOR = "bcf5db";
    
    // order matters
    private static final String[] LABELS = {LABEL_PREFIX + "description?",
                                            LABEL_PREFIX + "consensus?",
                                            LABEL_PREFIX + "architecture?",
                                            LABEL_PREFIX + "quality?",
                                            LABEL_PREFIX + "approved ✅",
                                            };

    private final Github gh;
    private final String[] committers;
    private final String[] pmc;
    // cache repo collaborators
    private Map<String, GHPersonSet<GHUser>> repoCollaborators;


    public Flinkbot(Github gh, String[] committers, String[] pmc) {
        this.gh = gh;
        this.botName = "@"+gh.getBotName();
        this.trackingMessage = "Thanks a lot for your contribution to the Apache Flink project. I'm the "+ botName +". I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Automated Checks" +
                "\n" +
                "Last runCheck on commit xxx (Fri, May 24, 2pm CET)\n" +
                "\n" +
                " ✅no warnings" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ❓ 1. The [description] looks good.\n" +
                "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❓ 3. Needs [attention] from.\n" +
                "* ❓ 4. The change fits into the overall [architecture].\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                " The Bot is tracking the review progress through labels. Labels are applied according to the order of the review items. " +
                "For consensus, approval by a Flink committer of PMC member is required" +
                " <summary>Bot commands</summary>\n" +
                "  The "+ botName +" bot supports the following commands:\n" +
                "\n" +
                " - `"+ botName +" approve description` to approve one or more aspects (aspects: `description`, `consensus`, `architecture` and `quality`)\n" +
                " - `"+ botName +" approve all` to approve all aspects\n" +
                " - `"+ botName +" approve-until architecture` to approve everything until `architecture`\n" +
                " - `"+ botName +" attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `"+ botName +" disapprove architecture` to remove an approval you gave earlier\n" +
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
            // LOG.debug("Checking PR " + pullToSimpleString(pr));
            return pullRequestHasComment(pr);
        });

        // put comment
        for (GHIssue pr : prs) {
            LOG.info("Commenting with tracking message on PR " + pullToSimpleString(pr));
            try {
                pr.comment(trackingMessage);
                // add label
                updateLabels(Collections.EMPTY_MAP, pr.getNumber());
            } catch (IOException e) {
                LOG.warn("Error writing tracking message", e);
            }
        }
        LOG.info("Done checking for new PRs. Requests remaining: " + gh.getRemainingRequests() +" Write requests " + gh.getRemainingWriteRequests());
    }

    private boolean pullRequestHasComment(GHIssue pr) {
        try {
            return pr.getComments().stream().anyMatch(comment -> {
                // call getUserName() to avoid an additional API request
                if (comment.getUserName().equals(gh.getBotName())) {
                    // runCheck if message is the same.
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
        return body.substring(0, Math.min(body.length(), 70)).equals(trackingMessage.substring(0, 70));
    }

    /**
     * This is processing all incoming mentions
     *
     * @param notifications new incoming notifications
     */
    public void processBotMentions(Iterator<GHThread> notifications) {
        while (notifications.hasNext()) {
            GHThread thread = notifications.next();
            LOG.info("Found a notification with title '" + thread.getTitle() + "'");
            try {
                if(thread.isRead()) {
                    LOG.debug("Skipping read notification with title "+thread.getTitle());
                    continue;
                }
                if (thread.getReason().equals("mention")) {
                    // we immediately mark the notification as read to avoid concurrency issues with newer comments
                    // being posted while still processing the old ones.
                    LOG.debug("Marking notification with reason '{}' and title '{}' as read", thread.getReason(), thread.getTitle());
                    thread.markAsRead();
                    Thread.sleep(1000); // + sleep some time to ensure we get new comments with this fetch

                    GHPullRequest boundPR = thread.getBoundPullRequest();
                    List<GHObject> commentsAndReviews = new ArrayList<>();
                    commentsAndReviews.addAll(boundPR.getComments());
                    commentsAndReviews.addAll(boundPR.listReviews().asList());

                    // sort by date, so that we process mentions in order
                    Collections.sort(commentsAndReviews, (o1, o2) -> {
                        try {
                            return o1.getCreatedAt().compareTo(o2.getCreatedAt());
                        } catch (IOException e) {
                            // Throw an exception here. IOExceptions should not happen (It's a mistake by the library)
                            LOG.warn("Error while sorting", e);
                            throw new RuntimeException("Error while sorting", e);
                        }
                    });

                    updatePullRequestThread(boundPR, commentsAndReviews);
                } else {
                    // we will not do anything with this notification.
                    LOG.debug("Marking notification with reason '{}' and title '{}' as read", thread.getReason(), thread.getTitle());
                    thread.markAsRead();
                }
            } catch (Throwable e) {
                LOG.warn("Error while processing notification", e);
            }
            LOG.info("Done processing notification. Requests remaining: " + gh.getRemainingRequests());
        }
    }



    /*private */ void updatePullRequestThread(GHPullRequest pullRequest, List<GHObject> comments) {
        if(comments == null) {
            LOG.warn("Notification without comments");
            return;
        }
        LOG.debug("Processing pull request thread with " + comments.size() + " comments");
        GHIssueComment trackingComment = null;
        final Map<String, Set<String>> trackedApprovals = new HashMap<>();
        final Set<String> attention = new HashSet<>();
        for(GHObject comment: comments) {
            try {
                // extract body and username.
                String commentBody;
                String commentUserName;
                if(comment instanceof GHIssueComment) {
                    commentBody = ((GHIssueComment) comment).getBody();
                    commentUserName = ((GHIssueComment) comment).getUserName();
                } else if(comment instanceof GHPullRequestReview) {
                    commentBody = ((GHPullRequestReview) comment).getBody();
                    commentUserName = ((GHPullRequestReview) comment).getUser().getLogin();
                } else {
                    throw new IllegalStateException("Unknown");
                }
                if(isTrackingMessage(commentBody) && comment instanceof GHIssueComment) {
                    trackingComment = (GHIssueComment) comment;
                }

                String[] commentLines = commentBody.split("\n");
                for(String line: commentLines) {
                    if(line.contains(botName)) {
                        // remove , or . in the line.
                        line = line.replaceAll("[,.!?]", "");
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
                                        if(tokens[j].length() > 1 && tokens[j].substring(0,1).equals("@")) {
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
                                                addApproval(trackedApprovals, validApproval, commentUserName);
                                            }
                                        } else {
                                            addApproval(trackedApprovals, approval, commentUserName);
                                            // go through additional approvals
                                            for(int j = i + 3; j < tokens.length; j++) {
                                                String additionalApproval = tokens[j];
                                                if(ArrayUtils.contains(VALID_APPROVALS, additionalApproval)) {
                                                    addApproval(trackedApprovals, additionalApproval, commentUserName);
                                                }
                                            }
                                        }
                                    }
                                    // ugly copy-paste action here
                                    if(action.equals("disapprove")) {
                                        if(approval.equals("all")) {
                                            for(String validApproval: VALID_APPROVALS) {
                                                removeApproval(trackedApprovals, validApproval, commentUserName);
                                            }
                                        } else {
                                            removeApproval(trackedApprovals, approval, commentUserName);
                                            // go through additional disapprovals
                                            for(int j = i + 3; j < tokens.length; j++) {
                                                String additionalApproval = tokens[j];
                                                if(ArrayUtils.contains(VALID_APPROVALS, additionalApproval)) {
                                                    removeApproval(trackedApprovals, additionalApproval, commentUserName);
                                                }
                                            }
                                        }
                                    }
                                } else if(action.equals("approve-until")) {
                                    if(!ArrayUtils.contains(VALID_APPROVALS, approval)) {
                                        LOG.debug("Invalid approval {} in '{}'", approval, line);
                                        break;
                                    }
                                    for(String approveUntil: VALID_APPROVALS) {
                                        addApproval(trackedApprovals, approveUntil, commentUserName);
                                        if(approveUntil.equals(approval)) {
                                            break;
                                        }
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
                LOG.warn("Error processing comment '"+comment+"'. Msg: "+t.getMessage(), t);
            }
        }

        // update tracking comment
        if(trackingComment == null) {
            LOG.warn("Invalid notification? comments do not contain tracking message " + comments);
            return; // leave method. Updating the labels also depends on a valid tracking comment
        } else {
            // generate comment
            StringBuffer newComment = new StringBuffer();
            String[] messageLines = trackingMessage.split("\n");
            boolean updateWarnings = false;
            for(String line: messageLines) {
                // decide whether we add something
                boolean tick = false;
                boolean attentionTick = false;
                String nextLine = null;
                for(String approval: VALID_APPROVALS) {
                    if(line.contains("[" + approval + "]")) {
                       String append = "    - Approved by ";
                       Set<String> approversSet = trackedApprovals.get(approval);
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

                        addAttentionToReviewers(attention, trackingComment.getParent().getNumber());
                    }
                }
                // (conditionally) copy the original line
                if(tick) {
                    if (attentionTick) {
                        newComment.append(line.replace("❓", "❗"));
                    } else {
                        newComment.append(line.replace("❓", "✅"));
                    }
                } else {
                    //normal behavior
                    if(!updateWarnings) {
                        newComment.append(line);
                    }
                }

                // set updateWarnings flag appropriately (to statically generate the warnings section)
                if(line.contains("Last runCheck on commit")) {
                    // extract from a string like:
                    // Last runCheck on commit 6586e48ad887669dbb14c26440964a913176ac12 (Fri, May 24, 2pm CET)
                    Matcher matcher = GET_SHA_PATTERN.matcher(line);
                    matcher.find();
                    String lastCheckSha = matcher.group(1);
                    if(!lastCheckSha.equals(pullRequest.getHead().getSha())) {
                        updateWarnings = true; // this will ignore the existing warning lines
                        newComment.append(generateWarningsSection(pullRequest, comments)); // This appends new warning lines
                    }
                }

                if(line.contains("## Review Progress")) {
                    updateWarnings = false;
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

                    // need to update
                    trackingComment.update(newCommentString);
                    LOG.info("Updating tracking comment on PR: " + pullToSimpleString(trackingComment.getParent()));
                }
            } catch (IOException e) {
                LOG.warn("Error updating tracking comment", e);
            }
        }

        // remove non-committer/PMC approvals, then update labels
        Set<String> committersAndPmc = new HashSet<>();
        for(String committer: committers) {
            committersAndPmc.add("@"+committer);
        }
        for(String pm: pmc) {
            committersAndPmc.add("@"+pm);
        }
        for(Map.Entry<String, Set<String>> approval: trackedApprovals.entrySet()) {
            Set<String> approvalSet = approval.getValue();
            if(approvalSet != null && approvalSet.size() > 0) {
                approvalSet.retainAll(committersAndPmc);
            }
        }
        updateLabels(trackedApprovals, trackingComment.getParent().getNumber());
    }

    private String generateWarningsSection(GHPullRequest pullRequest, List<GHObject> comments) {

        List<String> warnings = new ArrayList<>();
        for(PullRequestCheck check: PULL_REQUEST_CHECK) {
            warnings.add(check.runCheck(pullRequest, comments));
        }

        StringBuffer section = new StringBuffer();
        section.append("Last runCheck on commit " + pullRequest.getHead().getSha() + " (" + new Date() + ")\n\n");
        if(warnings.size() == 0) {
            section.append(" ✅no warnings");
        } else {
            section.append("**Warnings:**\n");
            for(String warning: warnings) {
                section.append(" * ");
                section.append(warning);
                section.append("\n");
            }
        }

        return section.toString();
    }

    /**
     * Add "attention" mentioned users to the reviewers
     *
     */
    private void addAttentionToReviewers(Set<String> attention, int prID) {
        Set<String> addReviewers = new HashSet<>(attention);
        try {
            GHPullRequest pullRequest = gh.getWriteableRepository().getPullRequest(prID);
            String fullRepoName = pullRequest.getRepository().getFullName();

            List<GHUser> reviewers = new ArrayList<>(pullRequest.getRequestedReviewers());
            for(GHUser reviewer: reviewers) {
                String login = reviewer.getLogin();
                addReviewers.remove(removeAt(login));
            }

            // add remaining reviewers

            LOG.info("Assigning new reviewers {} for PR #{}", addReviewers, prID);
            for(String toAddUsername: addReviewers) {
                GHUser user = getRepoCollaborators(fullRepoName).byLogin(removeAt(toAddUsername));
                if(user == null) {
                    LOG.info("User {} has not been added as a reviewer, because they are not a collaborator of the repo", toAddUsername);
                } else {
                    // user is a collaborator, so can be added to the reviewers
                    reviewers.add(user);
                }

            }
            pullRequest.requestReviewers(reviewers);

        } catch(Throwable e) {
            LOG.warn("Error while updating reviewers", e);
        }
    }

    private GHPersonSet<GHUser> getRepoCollaborators(String repo) {
        if(this.repoCollaborators == null) {
            repoCollaborators = new HashMap<>();
        }
        return repoCollaborators.computeIfAbsent(repo, gh::getCollaborators);
    }

    // remove @ at the beginning
    private static String removeAt(String in) {
        return in.substring(1);
    }

    /**
     * Update the labels of the PR based on the approvals
     */
    private void updateLabels(Map<String, Set<String>> approvals, int prID) {
        try {
            // get writable connection
            GHIssue pullRequest = gh.getWriteableRepository().getIssue(prID);
            boolean hasDescriptionApproval = hasApproval("description", approvals);
            boolean hasConsensusApproval = hasApproval("consensus", approvals);
            boolean hasArchitectureApproval = hasApproval("architecture", approvals);
            boolean hasQualityApproval = hasApproval("quality", approvals);
            // approvals are required in order
            String labelString = LABELS[0];
            if(hasDescriptionApproval) {
                labelString = LABELS[1];
                if(hasConsensusApproval) {
                    labelString = LABELS[2];
                    if(hasArchitectureApproval) {
                        labelString = LABELS[3];
                        if(hasQualityApproval) {
                            labelString = LABELS[4];
                        }
                    }
                }
            }

            // update labels
            Collection<GHLabel> labels = pullRequest.getLabels();
            GHLabel reviewLabel = null;
            for(GHLabel label: labels) {
                if(label.getName().startsWith(LABEL_PREFIX)) {
                    if(reviewLabel == null) {
                        reviewLabel = label;
                    } else {
                        LOG.warn("Detected multiple review labels on {}: {} and {}. Deleting it!", pullRequest, reviewLabel.getName(), label.getName());
                        pullRequest.removeLabels(label);
                    }
                }
            }
            if(reviewLabel == null) {
                // add label
                GHLabel ghLabel = createOrGetLabel(labelString, pullRequest.getRepository());
                pullRequest.addLabels(ghLabel);
                LOG.info("Adding label {} to PR {}", labelString, pullRequest.getTitle());
            } else if(!reviewLabel.getName().equals(labelString)) {
                LOG.info("Updating label from {} to {} on PR {}", reviewLabel.getName(), labelString, pullRequest.getTitle());
                pullRequest.removeLabels(reviewLabel);

                GHLabel ghLabel = createOrGetLabel(labelString, pullRequest.getRepository());
                pullRequest.addLabels(ghLabel);
            }
        } catch(Throwable e) {
            LOG.warn("Error while updating labels", e);
        }

    }

    private GHLabel createOrGetLabel(String labelString, GHRepository repository) throws IOException {
        try {
            return repository.getLabel(labelString);
        } catch(FileNotFoundException noLabel) {
            LOG.debug("Label '{}' did not exist", labelString, noLabel);
            LOG.info("Label '{}' did not exist, creating it", labelString);
            return repository.createLabel(labelString, LABEL_COLOR);
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

    private boolean hasApproval(String aspect, Map<String, Set<String>> approvals) {
        if(approvals == null) {
            return false;
        }

        Set<String> approvers = approvals.get(aspect);
        if(approvers == null) {
            return false;
        }
        return approvers.size() > 0;
    }

    private static void addApproval(Map<String, Set<String>> approvals, String approvalName, String userName) {
        Set<String> approver = approvals.get(approvalName);
        if (approver == null) {
            approver = new HashSet<>();
        }
        approver.add("@" + userName);
        approvals.put(approvalName, approver);
    }

    private void removeApproval(Map<String, Set<String>> trackedApprovals, String approval, String userName) {
        Set<String> approver = trackedApprovals.get(approval);
        if(approver == null) {
            approver = new HashSet<>();
        }
        approver.remove("@" + userName);
        trackedApprovals.put(approval, approver);
    }


    private static String pullToSimpleString(GHIssue pr) {
        return "#" + pr.getNumber() + ": " + pr.getTitle();
    }
}
