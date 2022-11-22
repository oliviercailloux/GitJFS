package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import org.eclipse.jgit.lib.ObjectId;

public interface GitPathRoot extends GitPath {

	GitPathRootShaCachedImpl toShaCached() throws IOException, NoSuchFileException;

	ImmutableList<GitPathRootShaImpl> getParentCommits() throws IOException, NoSuchFileException;

	/**
	 * If {@link Files#exists} returns {@code false}, an exception is thrown.
	 */
	Commit getCommit() throws IOException, NoSuchFileException;

	/**
	 * Returns the commit id contained in this root component, if any. The method is
	 * called <tt>static</tt> because the returned id is simply the one that was
	 * given when constructing this path. This method does not attempt to check that
	 * the returned id indeed corresponds to some commit in this file system.
	 *
	 * @return the commit id contained in this root component.
	 * @throws IllegalArgumentException iff this root component does not contain a
	 *                                  commit id
	 * @see #isCommitId()
	 */
	ObjectId getStaticCommitId();

	/**
	 * Returns the git ref contained in this root component, if any. The returned
	 * string starts with <tt>refs/</tt>, does not contain <tt>//</tt>, does not
	 * contain <tt>\</tt>, and does not end with <tt>/</tt>.
	 * <p>
	 * This method does not access the file system.
	 *
	 * @return the git ref contained in this root component.
	 * @throws IllegalStateException iff this root component does not contain a git
	 *                               ref
	 * @see #isRef()
	 */
	String getGitRef();

	/**
	 * Indicates whether this root component contains a commit id or a git ref.
	 *
	 * @return {@code true} iff this root component contains a commit id;
	 *         equivalently, iff this root component does not contain a git ref.
	 */
	boolean isCommitId();

	/**
	 * Indicates whether this root component contains a git ref or a commit id.
	 *
	 * @return {@code true} iff this root component contains a git ref;
	 *         equivalently, iff this root component does not contain a commit id.
	 */
	boolean isRef();

	/**
	 * Returns {@code null}.
	 *
	 * @return {@code null}
	 */
	@Override
	GitPathImpl getParent();

	/**
	 * Returns this path.
	 */
	@Override
	GitPathRootImpl toAbsolutePath();

	/**
	 * Returns itself.
	 *
	 * @return itself
	 */
	@Override
	GitPathRootImpl getRoot();

	@Override
	GitFileSystemImpl getFileSystem();

	GitPathRootShaImpl toSha() throws IOException, NoSuchFileException;

}
