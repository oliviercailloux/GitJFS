package io.github.oliviercailloux.git;

import static java.util.Objects.requireNonNull;

/**
 * The coordinates of a repository constituted of an authority, an owner and a
 * repository name. This format is used by such hosts as
 * <a href="https://github.com/">GitHub</a> or
 * <a href="https://bitbucket.org/">BitBucket</a>.
 */
public class RepositoryCoordinates {
	public static RepositoryCoordinates from(String authority, String owner, String repo) {
		return new RepositoryCoordinates(authority, owner, repo);
	}

	private final String authority;
	private final String owner;
	private final String repo;
	private final GitUri uri;

	private RepositoryCoordinates(String authority, String owner, String repo) {
		this.authority = requireNonNull(authority);
		this.owner = requireNonNull(owner);
		this.repo = requireNonNull(repo);
		uri = GitUri.ssh(authority, "/" + owner + "/" + repo + ".git");
	}

	public String authority() {
		return authority;
	}

	public String owner() {
		return owner;
	}

	public String repositoryName() {
		return repo;
	}

	public String id() {
		return owner + "/" + repo;
	}

	public GitUri asGitUri() {
		return uri;
	}
}
