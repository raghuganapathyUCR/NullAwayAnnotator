package edu.ucr.cs.riple.autofixer.metadata.trackers;

import edu.ucr.cs.riple.autofixer.FixType;
import edu.ucr.cs.riple.autofixer.metadata.AbstractRelation;
import edu.ucr.cs.riple.injector.Fix;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldUsageTracker extends AbstractRelation<TrackerNode> implements UsageTracker {

  private final FixType fixType;

  public FieldUsageTracker(String filePath) {
    super(filePath);
    this.fixType = FixType.CLASS_FIELD;
  }

  @Override
  protected TrackerNode addNodeByLine(String[] values) {
    return new TrackerNode(values[0], values[1], values[2], values[3]);
  }

  @Override
  public Set<String> getUsers(Fix fix) {
    if (!fix.location.equals(fixType.name)) {
      return null;
    }
    List<TrackerNode> nodes =
        findAllNodes(
            candidate ->
                candidate.calleeClass.equals(fix.className)
                    && candidate.calleeMember.equals(fix.param),
            fix.param,
            fix.className);
    if (nodes == null) {
      return null;
    }
    return nodes
        .stream()
        .map(fieldGraphNode -> fieldGraphNode.callerClass)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<Usage> getUsage(Fix fix) {
    if (!fix.location.equals(fixType.name)) {
      return null;
    }
    List<TrackerNode> nodes =
        findAllNodes(
            candidate ->
                candidate.calleeClass.equals(fix.className)
                    && candidate.calleeMember.equals(fix.param),
            fix.param,
            fix.className);
    Set<Usage> ans =
        nodes
            .stream()
            .map(fieldNode -> new Usage(fieldNode.callerMethod, fieldNode.callerClass))
            .collect(Collectors.toSet());
    ans.add(new Usage("null", fix.className));
    return ans;
  }
}