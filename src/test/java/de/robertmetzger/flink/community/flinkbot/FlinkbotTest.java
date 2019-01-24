package de.robertmetzger.flink.community.flinkbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.junit.Test;
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
            "* [ ] 1. The [contribution] is well-described.\n" +
            "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
            "* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
            "* [ ] 4. The [architecture] is sound.\n" +
            "* [ ] 5. Overall code [quality] is good.\n" +
            "\n" +
            "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

    /**
     * Emtpy comment list
     */
    @Test
    public void testProcessBotMentionsEmpty() {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();
        bot.processBotMentions(comments);
    }

    /**
     * Only the tracking message. Ensure the message is not changing.
     */
    @Test
    public void testProcessBotMentionsNoChanges() throws IOException {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));

        bot.processBotMentions(comments);

        // ensure comment.update() never got called
        verify(comments.get(0), never()).update(any());
    }

    /**
     * Ensure a simple "contribution" approval works
     */
    @Test
    public void testProcessBotMentionsSimple() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske\n" +
                "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "* [ ] 4. The [architecture] is sound.\n" +
                "* [ ] 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(getComment("@flinkbot approve contribution", "fhueske"));

        bot.processBotMentions(comments);

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(comments.get(0)).update(argument.capture());
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
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske, @trohrmann\n" +
                "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* [x] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "    - Needs attention by @uce\n" +
                "* [ ] 4. The [architecture] is sound.\n" +
                "* [ ] 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(getComment("@flinkbot approve contribution", "fhueske"));
        comments.add(getComment("@flinkbot approve consensus\n@flinkbot approve contribution\n@flinkbot attention @uce", "trohrmann"));
        comments.add(getComment("@flinkbot disapprove consensus", "trohrmann"));


        bot.processBotMentions(comments);

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
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske\n" +
                "* [x] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @fhueske\n" +
                "* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "* [x] 4. The [architecture] is sound.\n" +
                "    - Approved by @fhueske\n" +
                "* [x] 5. Overall code [quality] is good.\n" +
                "    - Approved by @fhueske\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(getComment("@flinkbot approve all", "fhueske"));

        bot.processBotMentions(comments);

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
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske, @rmetzger\n" +
                "* [x] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "    - Approved by @test\n" +
                "* [x] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "    - Needs attention by @rmetzger, @test, @test2, @uce\n" +
                "* [x] 4. The [architecture] is sound.\n" +
                "    - Approved by @test\n" +
                "* [x] 5. Overall code [quality] is good.\n" +
                "    - Approved by @test\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));

        comments.add(getComment("@flinkbot approve contribution", "fhueske"));
        comments.add(getComment("@flinkbot approve contribution", "fhueske"));
        comments.add(getComment("@flinkbot approve contribution", "fhueske"));
        comments.add(getComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(getComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(getComment("@flinkbot approve contribution", "rmetzger"));
        comments.add(getComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(getComment("@flinkbot attention @uce @rmetzger @test\n\n", "trohrmann"));
        comments.add(getComment("@flinkbot attention @test @rmetzger", "test2"));
        comments.add(getComment("@flinkbot attention @test2", "test"));
        comments.add(getComment("@flinkbot attention @test2", "test"));

        comments.add(getComment("@flinkbot approve consensus", "test"));
        comments.add(getComment("@flinkbot approve architecture", "test"));
        comments.add(getComment("@flinkbot approve quality", "test"));


        bot.processBotMentions(comments);

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
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske, @rmetzger\n" +
                "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* [x] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "    - Needs attention by @uce\n" +
                "* [ ] 4. The [architecture] is sound.\n" +
                "* [ ] 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* [x] 1. The [contribution] is well-described.\n" +
                "    - Approved by @fhueske, @hansi, @rmetzger\n" +
                "* [ ] 2. There is [consensus] that the contribution should go into to Flink.\n" +
                "* [x] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]\n" +
                "    - Needs attention by @uce\n" +
                "* [ ] 4. The [architecture] is sound.\n" +
                "* [ ] 5. Overall code [quality] is good.\n" +
                "\n" +
                "Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.";

        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment("Some other text here.", "rmetzger"));
        comments.add(getComment(INITIAL, "flinkbot"));
        comments.add(getComment("@flinkbot approve contribution", "fhueske"));
        comments.add(getComment("@flinkbot approve consensus", "trohrmann"));
        comments.add(getComment("@flinkbot approve contribution", "rmetzger"));
        comments.add(getComment("@flinkbot disapprove consensus", "trohrmann"));
        comments.add(getComment("@flinkbot attention @uce\n", "trohrmann"));
        comments.add(getComment("ASDjifejoi fjoif aweof pojaewf ijwef jiwg rjeg ijreg ", "rmetzger"));
        comments.add(getComment("@flinkbot approve contribution", "hansi"));


        bot.processBotMentions(comments);

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
        testCommand("@flinkbot aisdj contribution");
        testCommand("@flinkbot    ");
        testCommand("@flinkbot");
        testCommand("@flinkbot approve attention");
        testCommand("@flinkbot attention attention");
        testCommand("@flinkbot attention attention attention");
    }

    private static void testCommand(String command) throws IOException {
        Github gh = mock(Github.class);
        when(gh.getBotName()).thenReturn("flinkbot");

        Flinkbot bot = new Flinkbot(gh);
        List<GHIssueComment> comments = new ArrayList<>();

        comments.add(getComment(TRACKING_MESSAGE, "flinkbot"));
        comments.add(getComment(command, "fhueske"));

        bot.processBotMentions(comments);

        // ensure comment.update() never got called --> Because wrong commands should not update the tracking message
        verify(comments.get(0), never()).update(any());
    }

    private static GHIssueComment getComment(String body, String user) {
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getBody()).thenReturn(body);
        when(comment.getUserName()).thenReturn(user);
        return comment;
    }
}
