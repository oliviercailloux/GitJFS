package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitPathRootRefImpl extends GitPathRootImpl implements GitPathRootRef {

	private GitPathRootShaImpl sha;

	protected GitPathRootRefImpl(GitFileSystemImpl fileSystem, GitRev gitRev) {
		super(fileSystem, gitRev);
		checkArgument(gitRev.isRef());
		sha = null;
	}

	@Override
	public GitPathRootShaImpl toSha() throws IOException, NoSuchFileException {
		refreshCache();
		if (sha == null) {
			throw new NoSuchFileException(toString());
		}
		return sha;
	}

	private void refreshCache() throws IOException, NoSuchFileException {
		final Optional<ObjectId> newIdOpt = getFileSystem().getObjectId(getGitRef());
		final GitPathRootShaImpl newSha;
		if (newIdOpt.isPresent()) {
			final ObjectId newId = newIdOpt.get();
			/**
			 * We try to hold to our existing reference if possible, because it may contain
			 * valuable cache data.
			 */
			if (sha == null || !sha.getStaticCommitId().equals(newId)) {
				newSha = getFileSystem().getPathRoot(newId);
			} else {
				newSha = sha;
			}
		} else {
			newSha = null;
		}
		sha = newSha;
	}

	@Override
	RevCommit getRevCommit() throws IOException, NoSuchFileException {
		return toSha().getRevCommit();
	}

}
