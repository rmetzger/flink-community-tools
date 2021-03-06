package de.robertmetzger.flink.community.flinkbot;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.*;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Unit test for FlinkbotTest.
 */
public class FlinkbotTest {
    private static final String TRACKING_MESSAGE = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
            "to review your pull request. We will use this comment to track the progress of the review.\n" +
            "\n" +
            "\n" +
            "## Review Progress\n" +
            "\n" +
            "* ❓ 1. The [description] looks good.\n" +
            "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
            "* ❓ 3. Needs [attention] from.\n" +
            "* ❓ 4. The change fits into the overall [architecture].\n" +
            "* ❓ 5. Overall code [quality] is good.\n";

    private static final String TRACKING_MESSAGE_2 =                 "\n" +
            "Please see the [Pull Request Review Guide](https://flink.apache.org/contributing/reviewing-prs.html) for a full explanation " +
            "of the review process." +
            "<details>\n" +
            " The Bot is tracking the review progress through labels. Labels are applied according to the order of the review items. " +
            "For consensus, approval by a Flink committer of PMC member is required" +
            " <summary>Bot commands</summary>\n" +
            "  The @flinkbot bot supports the following commands:\n" +
            "\n" +
            " - `@flinkbot approve description` to approve one or more aspects (aspects: `description`, `consensus`, `architecture` and `quality`)\n" +
            " - `@flinkbot approve all` to approve all aspects\n" +
            " - `@flinkbot approve-until architecture` to approve everything until `architecture`\n" +
            " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
            " - `@flinkbot disapprove architecture` to remove an approval you gave earlier\n" +
            "</details>";

    private static String[] pmc = {"fhueske", "rmetzger"};
    // this is obviously wrong information, for the sake of testing only. sorry :(
    private static String[] committer = {"trohrmann", "uce"};

    /**
     * Emtpy comment list
     */
    @Test
    public void testProcessBotMentionsEmpty() throws IOException {
        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();
        bot.updatePullRequestThread(getMockedPullRequest(), comments);
    }

    /**
     * Only the tracking message. Ensure the message is not changing.
     */
    @Test
    public void testProcessBotMentionsNoChanges() throws IOException {
        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE + TRACKING_MESSAGE_2, "flinkbot"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        // ensure comment.update() never got called
        verify((GHIssueComment)comments.get(0), never()).update(any());
        // ensure "review=needsDescriptionApproval ❓" label has been set

    }

    /**
     * Ensure a simple "description" approval works
     */
    @Test
    public void testProcessBotMentionsSimple() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❓ 3. Needs [attention] from.\n" +
                "* ❓ 4. The change fits into the overall [architecture].\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }

    /**
     * Ensure multiple approvals work
     **/
    @Test
    public void testProcessBotMultipleApprovals() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ✅ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 3. Needs [attention] from.\n" +
                "* ✅ 4. The change fits into the overall [architecture].\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description consensus architecture", "fhueske"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }



    /**
     * Ensure a complex example
     */
    @Test
    public void testProcessBotMentionsComplex1() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC], @trohrmann [committer]\n" +
                "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❓ 4. The change fits into the overall [architecture].\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description.", "fhueske")); // this tests including a "." (dot) at the end
        comments.add(createComment("@flinkbot approve consensus\n@flinkbot approve description\n@flinkbot attention @uce", "trohrmann"));
        comments.add(createComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(createComment("@flinkbot approve consensus", "hans"));
        comments.add(createComment("@flinkbot disapprove all", "hans"));
        comments.add(createComment("@flinkbot approve consensus description", "hans"));
        comments.add(createComment("@flinkbot disapprove consensus description", "hans"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());

        // validate labels
        assertEquals("review=consensus?", getLabelsFromMock(gh));
    }



    /**
     * "approve all" should work
     */
    @Test
    public void testProcessBotMentionsApproveAll() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ✅ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 3. Needs [attention] from.\n" +
                "* ✅ 4. The change fits into the overall [architecture].\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ✅ 5. Overall code [quality] is good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve all.", "fhueske")); // even with a dot in the end.

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }

    /**
     * "approve-until architecture"
     */
    @Test
    public void testProcessBotMentionsApproveUntil() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ✅ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 3. Needs [attention] from.\n" +
                "* ✅ 4. The change fits into the overall [architecture].\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve-until architecture.", "fhueske"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }

    /**
     * Ensure a complex example
     * - duplicate approvals
     * - long attention lists
     * - duplicate attention mentions
     * - everything approved
     */
    @Test
    public void testProcessBotMentionsComplex2() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC], @rmetzger [PMC]\n" +
                "* ✅ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @test\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @rmetzger [PMC], @test, @test2, @uce [committer]\n" +
                "* ✅ 4. The change fits into the overall [architecture].\n" +
                "    - Approved by @test\n" +
                "* ✅ 5. Overall code [quality] is good.\n" +
                "    - Approved by @test\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));

        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(createComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(createComment("@flinkbot approve description", "rmetzger"));
        comments.add(createComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(createComment("@flinkbot attention @uce @rmetzger @test\n\n", "trohrmann"));
        comments.add(createComment("@flinkbot attention @test @rmetzger", "test2"));
        comments.add(createComment("@flinkbot attention @test2", "test"));
        comments.add(createComment("@flinkbot attention @test2", "test"));

        comments.add(createComment("@flinkbot approve consensus", "test"));
        comments.add(createComment("@flinkbot approve architecture", "test"));
        comments.add(createComment("@flinkbot approve quality", "test"));


        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }

    /**
     * Edit an existing comment
     */
    @Test
    public void testProcessBotMentionsEditExisting() throws IOException {
        final String INITIAL = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC], @rmetzger [PMC]\n" +
                "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❓ 4. The change fits into the overall [architecture].\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC], @hansi, @rmetzger [PMC]\n" +
                "* ❓ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❓ 4. The change fits into the overall [architecture].\n" +
                "* ❓ 5. Overall code [quality] is good.\n" +
                TRACKING_MESSAGE_2;

        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment("Some other text here.", "rmetzger"));
        comments.add(createComment(INITIAL, "flinkbot"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(createComment("@flinkbot approve description", "rmetzger"));
        comments.add(createComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(createComment("@flinkbot attention @uce\n", "trohrmann"));
        comments.add(createComment("ASDjifejoi fjoif aweof pojaewf ijwef jiwg rjeg ijreg ", "rmetzger"));
        comments.add(createComment("@flinkbot approve description", "hansi"));


        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify((GHIssueComment)comments.get(1)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }


    /**
     * Invalid commands
     */
    @Test
    public void testProcessBotMentionsInvalidCommands() throws IOException {
        testCommand("@flinkbot aisdj ioajsd");
        testCommand("@flinkbot aisdj description");
        testCommand("@flinkbot    ");
        testCommand("@flinkbot");
        testCommand("@flinkbot approve attention");
        testCommand("@flinkbot attention attention");
        testCommand("@flinkbot attention attention attention");
    }

    private static void testCommand(String command) throws IOException {
        Github gh = getMockedGitHub();

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHObject> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE + TRACKING_MESSAGE_2, "flinkbot"));
        comments.add(createComment(command, "fhueske"));

        bot.updatePullRequestThread(getMockedPullRequest(), comments);

        // ensure comment.update() never got called --> Because wrong commands should not update the tracking message
        verify((GHIssueComment)comments.get(0), never()).update(any());
    }

    @Test
    public void testRegex() {
        String input = "Last runCheck on commit 6586e48ad887669dbb14c26440964a913176ac12 (Fri, May 24, 2pm CET)";
        Pattern GET_SHA_PATTERN = Pattern.compile(".* ([a-z0-9]{40}) \\(.*\\)");
        Matcher match = GET_SHA_PATTERN.matcher(input);

        Assert.assertEquals("6586e48ad887669dbb14c26440964a913176ac12", match.group(1));
    }

    // ------------------------------------ testing tools ------------------------------------

    private static GHIssueComment createComment(String body, String user) {
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getBody()).thenReturn(body);
        when(comment.getUserName()).thenReturn(user);
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(666);
        when(issue.getTitle()).thenReturn("Mock title");
        when(comment.getParent()).thenReturn(issue);
        return comment;
    }

    private static class TestGHIssue extends GHIssue {
        private GHRepository repo;
        private Collection<GHLabel> myLabels = new ArrayList<>();

        public TestGHIssue(GHRepository repo) {
            this.repo = repo;
        }

        @Override
        public Collection<GHLabel> getLabels() throws IOException {
            return myLabels;
        }

        @Override
        public GHRepository getRepository() {
            return repo;
        }

        @Override
        public void addLabels(GHLabel... names) throws IOException {
            myLabels.addAll(Arrays.asList(names));
        }
    }

    private static Github getMockedGitHub() throws IOException {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");
        GHRepository repo = mock(GHRepository.class);
        when(gh.getWriteableRepository()).thenReturn(repo);
        GHIssue issue = new TestGHIssue(repo);
        when(repo.getIssue(666)).thenReturn(issue);
        when(repo.getLabel(any())).then((Answer<GHLabel>) invocation -> {
            String name = invocation.getArgument(0);
            GHLabel answer = mock(GHLabel.class);
            when(answer.getName()).thenReturn(name);
            return answer;
        });
        return gh;
    }

    private static GHPullRequest getMockedPullRequest() {
        GHPullRequest pr = mock(GHPullRequest.class);
        return pr;
    }

    private static String getLabelsFromMock(Github gh) throws IOException {
        return gh.getWriteableRepository().getIssue(666).getLabels()
                .stream()
                .map(GHLabel::getName)
                .collect(Collectors.joining(","));
    }


}
