/**
 * Here are a few things I wonder about related to Java file systems. If you
 * have some answers please write to me.
 *
 * {@link java.nio.file.spi.FileSystemProvider#readSymbolicLink} does not
 * document that it may throw {@link java.nio.file.NoSuchFileException}, whereas
 * this possible exception is mentioned in similar methods. Is this a mistake?
 * If not, what should this method throw when trying to read a non existent
 * path?
 * <p>
 * {@link java.nio.file.FileSystem#getRootDirectories()}: I have no idea why the
 * Java Files API does not want an IOException here.
 */
package io.github.oliviercailloux.gitjfs;
