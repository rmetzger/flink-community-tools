# Flink Community Tools

## Pull request review status bot for GitHub

Description: This effort is related to [this pull request](https://github.com/apache/flink/pull/6873).
On each new pull request, automatically post a comment with the following contents:
```
Thanks a lot for your contribution to the Apache Flink project. I'm the @flinkbot. I help the community
to review your pull request. We will use this comment to track the progress of the review.


# Review Progress

* [ ] 1. The [contribution] is well-described.
* [ ] 2. There is [consensus] that the contribution should go into to Flink.
* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]
* [ ] 4. The [architecture] is sound.
* [ ] 5. Overall code [quality] is good.

Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.
```

Check each subsequent comment for a mention:

* `@flinkbot contribution approve`: Update original comment and put the author as a approver of a review
* `@flinkbot contribution disapprove`: Remove author as an approver
* .. add approve / disapprove for `contribution`, `consensus`, `architecture`, `architecture`
* For `attention @fhueske` add the name to the list of "attention payers".

After a series of comments, the review progress could for example look like this:

```
# Review Progress

* [ ] 1. The [contribution] is well-described.
    - Approved by @twalthr [PMC], @johndoe 
* [ ] 2. There is [consensus] that the contribution should go into to Flink.
* [ ] 3. [Does not need specific [attention] | Needs specific attention for X | Has attention for X by Y]
* [ ] 4. The [architecture] is sound.
    - Approved by @pnowojski [PMC], @johndoe
* [ ] 5. Overall code [quality] is good.

Please see the [Pull Request Review Guide](https://flink.apache.org/reviewing-prs.html) if you have questions about the review process.
```

### Feature requests

* [ ] could it add the mentioned person to `reviewers`, when using `@flinkbot attention`
* [x] could it tick the boxes?
* [x] support `approve all`
* [ ] if the bot is mentioned in a thread without a tracking message: post one (limit to configured repo)
* [ ] attention-payers can tell the bot that they approve.


## Future projects
* For the PR bot, put a label whether a PR is ready to merge
* Flink community metrics
* Pull requests dashboard
* Automatic labeling of pull requests (into project components)
* Automatic merging of pull requests by the bot
	IF: 
	- all review items have been approved
	- travis is green
	- a committer has labeled the PR to be merged

## License

MIT License (see `license` file).
