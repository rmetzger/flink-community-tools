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
    public void testProcessBotMentions() throws IOException {
        final String EXPECTED = "Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community\n" +
                "to review your pull request. We will use this comment to track the progress of the review.\n" +
                "\n" +
                "\n" +
                "## Review Progress\n" +
                "\n" +
                "* [ ] 1. The [contribution] is well-described.\n" +
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

    private static GHIssueComment getComment(String body, String user) {
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getBody()).thenReturn(body);
        when(comment.getUserName()).thenReturn(user);
        return comment;
    }
}
