package io.github.oliviercailloux.gitjfs;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

/**
 * A git file system that rests on a (user-managed) {@link DfsRepository}.
 *
 * @see IGitFileSystemProvider#newFileSystemFromDfsRepository(DfsRepository)
 */
public interface IGitDfsFileSystem extends IGitFileSystem {
	DfsRepository getRepository();
}
