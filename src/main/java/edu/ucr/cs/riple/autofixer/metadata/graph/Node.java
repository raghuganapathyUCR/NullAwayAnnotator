package edu.ucr.cs.riple.autofixer.metadata.graph;

import edu.ucr.cs.riple.autofixer.metadata.UsageTracker;
import edu.ucr.cs.riple.injector.Fix;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Node {
  public final Fix fix;
  public final Set<UsageTracker.Usage> usages;
  public final Set<String> classes;
  public int referred;
  public int effect;
  public int id;
  public boolean isDangling;

  public Node(Fix fix) {
    this.fix = fix;
    isDangling = false;
    this.usages = new HashSet<>();
    this.classes = new HashSet<>();
  }

  @Override
  public int hashCode() {
    return Objects.hash(fix.index, fix.className, fix.method);
  }

  public void updateUsages(Set<UsageTracker.Usage> usages) {
    this.usages.addAll(usages);
    this.classes.addAll(usages.stream().map(usage -> usage.clazz).collect(Collectors.toSet()));
    for (UsageTracker.Usage usage : usages) {
      if (usage.method == null || usage.method.equals("null")) {
        isDangling = true;
        break;
      }
    }
  }

  public boolean hasConflictInUsage(Node node) {
    if (node.isDangling || this.isDangling) {
      return !Collections.disjoint(node.classes, this.classes);
    }
    return !Collections.disjoint(node.usages, this.usages);
  }

  @Override
  public String toString() {
    return "Node{"
        + "fix=["
        + fix.method
        + " "
        + fix.className
        + " "
        + fix.param
        + " "
        + fix.location
        + "]"
        + ", id="
        + id
        + ", effect="
        + effect
        + ", referred="
        + referred
        + ", isDangling="
        + isDangling
        + '}';
  }
}
