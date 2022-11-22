package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A git path with no root component and a non-empty sequence of names.
 * <p>
 * Git relative paths are associated to a git absolute path, named “their
 * absolute equivalent”, to which they delegate most of their operations. The
 * absolute equivalent of a relative path has the main branch as root component
 * and has the same internal path except that its internal path is absolute
 * instead of relative.
 */
abstract class GitRelativePath extends GitPathImpl {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRelativePath.class);

	private static Path toJimFsRelativePath(List<String> names) throws InvalidPathException {
		/**
		 * NOT equivalent to resolve each component to the previous part, starting with
		 * JIM_FS_EMPTY: if one starts with a slash, this makes the resulting path
		 * absolute.
		 */
		final String first = names.isEmpty() ? "" : names.get(0);
		final String[] more = names.isEmpty() ? new String[] {}
				: names.subList(1, names.size()).toArray(new String[] {});
		final Path internalPath = GitFileSystemImpl.JIM_FS.getPath(first, more);
		if (internalPath.isAbsolute()) {
			throw new InvalidPathException(first, first + " makes this internal path absolute.");
		}
		return internalPath;
	}

	static GitRelativePath relative(GitFileSystemImpl fs, List<String> names) throws InvalidPathException {
		final Path internalPath = toJimFsRelativePath(names);
		return relative(fs, internalPath);
	}

	static GitRelativePath relative(GitFileSystemImpl fs, Path internalPath) {
		checkArgument(!internalPath.isAbsolute());
		checkArgument(internalPath.getNameCount() >= 1);

		if (internalPath.toString().equals("")) {
			return fs.emptyPath;
		}

		final GitPathRootImpl root = fs.mainSlash;
		final GitAbsolutePathWithInternal absolute = new GitAbsolutePathWithInternal(root,
				internalPath.toAbsolutePath());
		return new GitRelativePathWithInternal(absolute);
	}

	protected GitRelativePath() {
	}

	@Override
	public GitFileSystemImpl getFileSystem() {
		return toAbsolutePath().getFileSystem();
	}

	@Override
	public boolean isAbsolute() {
		return false;
	}

	/**
	 * Returns a git path whose root component refers to the main branch.
	 */
	@Override
	public abstract GitPathImpl toAbsolutePath();

	@Override
	public GitPathRootImpl getRoot() {
		return null;
	}

	@Override
	GitRelativePath toRelativePath() {
		return this;
	}
}
