package io.github.oliviercailloux.gitjfs;

import java.time.ZonedDateTime;

public record CommitSignatureImpl(String name, String email, ZonedDateTime date) implements CommitSignature {
  
}
