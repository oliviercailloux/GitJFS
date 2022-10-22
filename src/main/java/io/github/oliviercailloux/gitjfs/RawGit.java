package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.eclipse.jgit.lib.Ref;

public interface RawGit extends AutoCloseable {
	@Override
	public void close();

	public ImmutableList<Ref> refs() throws IOException;

	boolean isOpen();

}
