# Flink Pull request labeler

- on start, go over each Flink pull request (open or closed) and check if it has a component label applied to it
    - store the PR's JIRA ID + component in a local database.
- for each new pull request, get the component from JIRA. Attach it as a label, store in database
- Regularly, get the most recently updated Flink Jira tickets and check if the component has changed.


Approach 1:

| pullRequestId | JIRA ID   | componentOnPr | componentInJira   | PRlastUpdated | JIRAlastUpdated |
|---------------|-----------|---------------|-------------------|
|123            |FLINK-123  |Documentation  |Documentation      |


- start a "check for new PRs" thread
    - get PRs from GitHub, ordered by (newest first)
    - if pull request doesn't exist in table, add it (pullRequestId, JIRA ID, PR Last updated)
    - order table by "PRlastUpdated" (newest first)
    - check for each PR if the extracted JIRA ID is still the same, till we reach PRs which have not been changed
        - if not, update JIRA ID and clear "componentOnPr" field
        - update "PRlastUpdated" field
- start a "check for updated JIRAs" thread
    - order table by "JiraLastUpdated"
    - get recently updated JIRAs, till we reach JIRAs which have not been changed
    - for each JIRA, set the "componentInJira". TODO
    - update "JiraLastUpdated"
- start a "labeler" thread
    - get all entries where componentOnPr != componentInJira
    - add component on GitHub
    - update componentOnPr field
    
    
    
Approach 2:

- get all GitHub PRs (must be cached to stay within the GH rate limit :) )
- for each GH PR, get the component from JIRA
- if component has changed (or is not set), label it.
- done.

- getting component from JIRA is cached using a table.

JIRA-ID, component (🍭 separated) , lastUpdated

if entry doesn't exist in table, fetch it from JIRA, otherwise, return value from table.

- run a thread which checks recently updated JIRAs
- if a JIRA has been updated, remove it from cache table
- store point in time till when we've checked tickets