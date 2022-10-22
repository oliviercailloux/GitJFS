package io.github.oliviercailloux.gitjfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.Streams;
import java.nio.file.Path;

class GitRelativePathWithInternal extends GitRelativePath {
	private final Path internalPath;

	GitRelativePathWithInternal(Path internalPath) {
		checkArgument(internalPath.getRoot() == null);
		checkArgument(internalPath.getNameCount() >= 1);
		final boolean hasEmptyName = Streams.stream(internalPath).anyMatch(p -> p.toString().isEmpty());
		if (hasEmptyName) {
			verify(internalPath.getNameCount() == 1);
		}
		this.internalPath = checkNotNull(internalPath);
	}

	@Override
	Path getInternalPath() {
		return internalPath;
	}
}
