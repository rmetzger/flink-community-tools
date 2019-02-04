package de.robertmetzger.flink.community.flinkbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            "* ❌ 1. The [description] looks good.\n" +
            "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
            "* ❔ 3. Needs [attention] from.\n" +
            "* ❌ 4. The [architecture] is sound.\n" +
            "* ❌ 5. Overall code [quality] is good.\n" +
            "\n" +
            "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
            "of the review process." +
            "<details>\n" +
            "  <summary>Bot commands</summary>\n" +
            "  The @flinkbot bot supports the following commands:\n" +
            "\n" +
            " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
            " - `@flinkbot approve all` to approve all aspects\n" +
            " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
            " - `@flinkbot disapprove architecture` to remove an approval\n" +
            "</details>";

    private static String[] pmc = {"fhueske", "rmetzger"};
    // this is obviously wrong information, for the same of testing only. sorry :(
    private static String[] committer = {"trohrmann", "uce"};

    /**
     * Emtpy comment list
     */
    @Test
    public void testProcessBotMentionsEmpty() {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();
        bot.updatePullRequestThread(comments);
    }

    /**
     * Only the tracking message. Ensure the message is not changing.
     */
    @Test
    public void testProcessBotMentionsNoChanges() throws IOException {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));

        bot.updatePullRequestThread(comments);

        // ensure comment.update() never got called
        verify(comments.get(0), never()).update(any());
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
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❔ 3. Needs [attention] from.\n" +
                "* ❌ 4. The [architecture] is sound.\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));

        bot.updatePullRequestThread(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }

    /*
     * Ensure a multiple approval work

    @Test
    public void testProcessBotMultipleApprovals() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske\n" +
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @fhueske\n" +
                "* ❌ 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "* ❌ 4. The [architecture] is sound.\n" +
                "    - Approved by @fhueske\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have " +
                "questions about the review process or the usage of this bot";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description consensus architecture", "fhueske"));

        bot.processBotMentions(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
    }
     */

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
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❌ 4. The [architecture] is sound.\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve consensus\n@flinkbot approve description\n@flinkbot attention @uce", "trohrmann"));
        comments.add(createComment("@flinkbot disapprove consensus", "trohrmann"));


        bot.updatePullRequestThread(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
        assertEquals(EXPECTED, argument.getValue());
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
                "* ❔ 3. Needs [attention] from.\n" +
                "* ✅ 4. The [architecture] is sound.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "* ✅ 5. Overall code [quality] is good.\n" +
                "    - Approved by @fhueske [PMC]\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment("@flinkbot approve all", "fhueske"));

        bot.updatePullRequestThread(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
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
                "* ✅ 4. The [architecture] is sound.\n" +
                "    - Approved by @test\n" +
                "* ✅ 5. Overall code [quality] is good.\n" +
                "    - Approved by @test\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

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


        bot.updatePullRequestThread(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
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
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❌ 4. The [architecture] is sound.\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* ✅ 1. The [description] looks good.\n" +
                "    - Approved by @fhueske [PMC], @hansi, @rmetzger [PMC]\n" +
                "* ❌ 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* ❗ 3. Needs [attention] from.\n" +
                "    - Needs attention by @uce [committer]\n" +
                "* ❌ 4. The [architecture] is sound.\n" +
                "* ❌ 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) for a full explanation " +
                "of the review process." +
                "<details>\n" +
                "  <summary>Bot commands</summary>\n" +
                "  The @flinkbot bot supports the following commands:\n" +
                "\n" +
                " - `@flinkbot approve description` to approve the 1st aspect (similarly, it also supports the `consensus`, `architecture` and `quality` keywords)\n" +
                " - `@flinkbot approve all` to approve all aspects\n" +
                " - `@flinkbot attention @username1 [@username2 ..]` to require somebody's attention\n" +
                " - `@flinkbot disapprove architecture` to remove an approval\n" +
                "</details>";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment("Some other text here.", "rmetzger"));
        comments.add(createComment(INITIAL, "flinkbot"));
        comments.add(createComment("@flinkbot approve description", "fhueske"));
        comments.add(createComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(createComment("@flinkbot approve description", "rmetzger"));
        comments.add(createComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(createComment("@flinkbot attention @uce\n", "trohrmann"));
        comments.add(createComment("ASDjifejoi fjoif aweof pojaewf ijwef jiwg rjeg ijreg ", "rmetzger"));
        comments.add(createComment("@flinkbot approve description", "hansi"));


        bot.updatePullRequestThread(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(1)).update(argument.capture());
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
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh, committer, pmc);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(createComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(createComment(command, "fhueske"));

        bot.updatePullRequestThread(comments);

        // ensure comment.update() never got called --> Because wrong commands should not update the tracking message
        verify(comments.get(0), never()).update(any());
    }

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
}
