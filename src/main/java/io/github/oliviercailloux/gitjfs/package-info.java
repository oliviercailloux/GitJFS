/**
 * Here are a few things I wonder about related to Java file systems. If you
 * have some answers please write to me.
 *
 * {@link java.nio.file.spi.FileSystemProvider#readSymbolicLink} does not
 * document that it may throw {@link java.nio.file.NoSuchFileException}, whereas
 * this possible exception is mentioned in similar methods. Is this a mistake?
 * If not, what should this method throw when trying to read a non existent
 * path?
 */
package io.github.oliviercailloux.gitjfs;
