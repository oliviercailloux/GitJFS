package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A git path root is an absolute git path that has an empty sequence of names.
 * In other words, it consists in a root component only. Its string form ends
 * with <code>//</code>.
 * <p>
 * Note that the commit referred to (possibly indirectly) by this git path root
 * may not exist in the associated git file system. This occurs when either:
 * </p>
 * <ul>
 * <li>this path root contains a git ref which does not exist in this
 * repository;</li>
 * <li>this path root contains a git ref which refers to a sha that is not a
 * commit;</li>
 * <li>this path root contains a sha that is not a commit or does not
 * exist.</li>
 * </ul>
 *
 * @see GitPath
 */
public interface GitPathRoot extends GitPath {

	GitPathRootShaCached toShaCached() throws IOException, NoSuchFileException;

	ImmutableList<? extends GitPathRootSha> getParentCommits() throws IOException, NoSuchFileException;

	/**
	 * If {@link Files#exists} returns {@code false}, an exception is thrown.
	 */
	Commit getCommit() throws IOException, NoSuchFileException;

	/**
	 * Returns the commit id contained in this root component, if any. The method is
	 * called <code>static</code> because the returned id is simply the one that was
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
	 * string starts with <code>refs/</code>, does not contain <code>//</code>, does
	 * not contain <code>\</code>, and does not end with <code>/</code>.
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
	@Deprecated
	GitPath getParent();

	/**
	 * Returns this path.
	 */
	@Override
	GitPathRoot toAbsolutePath();

	/**
	 * Returns itself.
	 *
	 * @return itself
	 */
	@Override
	GitPathRoot getRoot();

	@Override
	GitFileSystem getFileSystem();

	GitPathRootSha toSha() throws IOException, NoSuchFileException;

}
