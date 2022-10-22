package io.github.oliviercailloux.gitjfs;

import java.nio.file.Path;

/**
 * An empty path. Each git file system is associated to a unique empty path.
 * This class should therefore have a unique instance per git file system. Use
 * {@link GitFileSystem#emptyPath} rather than creating a new one.
 */
class GitEmptyPath extends GitRelativePath {
	GitEmptyPath() {
	}

	@Override
	Path getInternalPath() {
		return GitFileSystem.JIM_FS_EMPTY;
	}
}
