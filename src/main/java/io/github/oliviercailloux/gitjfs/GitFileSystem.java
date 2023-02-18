package io.github.oliviercailloux.gitjfs;

import java.nio.file.FileSystem;

/**
 * Two such instances are equal iff they are the same instance.
 */
public abstract class GitFileSystem extends FileSystem implements IGitFileSystem {

}
