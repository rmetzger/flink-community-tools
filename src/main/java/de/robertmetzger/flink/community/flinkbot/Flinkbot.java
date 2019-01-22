package de.robertmetzger.flink.community.flinkbot;

public class Flinkbot {

    private final Github gh;

    public Flinkbot(Github gh) {
        this.gh = gh;
    }

    /**
     * Check if there are new pull requests w/o a managed comment yet.
     *  Create comment
     */
    public void checkForNewPRs() {
        gh.sa
    }

    public void checkForNewActions() {
    }
}
