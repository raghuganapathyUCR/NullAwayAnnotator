package edu.ucr.cs.riple.diagnose;

import edu.riple.annotationinjector.Fix;
import edu.riple.annotationinjector.Injector;
import edu.riple.annotationinjector.WorkList;
import edu.riple.annotationinjector.WorkListBuilder;
import edu.ucr.cs.riple.AutoFix;
import edu.ucr.cs.riple.NullAwayAutoFixExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Diagnose extends DefaultTask {

  String executable;
  Injector injector;
  Project project;
  String buildCommand;
  String resetCommand;
  Map<Fix, DiagnoseReport> fixReportMap;

  @TaskAction
  public void diagnose() {
    NullAwayAutoFixExtension autoFixExtension =
        getProject().getExtensions().findByType(NullAwayAutoFixExtension.class);
    String fixPath;
    if (autoFixExtension == null) {
      autoFixExtension = new NullAwayAutoFixExtension();
    }
    DiagnoseExtension diagnoseExtension = getProject().getExtensions().findByType(DiagnoseExtension.class);
    if(diagnoseExtension != null){
      fixPath = diagnoseExtension.getFixPath();
    }else{
      fixPath =
              autoFixExtension.getFixPath() == null
                      ? getProject().getProjectDir().getAbsolutePath()
                      : autoFixExtension.getFixPath();
      fixPath = (fixPath.endsWith("/") ? fixPath : fixPath + "/") + "fixes.json";
    }

    executable = autoFixExtension.getExecutable();
    injector =
            Injector.builder()
                    .setMode(Injector.MODE.BATCH)
                    .setCleanImports(false)
                    .build();
    project = getProject();
    detectCommands();

    System.out.println("Reset command: " + resetCommand);
    System.out.println("Build command: " + buildCommand);

    fixReportMap = new HashMap<>();
    List<WorkList> workListLists = new WorkListBuilder(fixPath).getWorkLists();

    DiagnoseReport base = makeReport();
    for(WorkList workList: workListLists){
      for(Fix fix: workList.getFixes()){
        injector.start(Collections.singletonList(new WorkList(Collections.singletonList(fix))));
        fixReportMap.put(fix, makeReport());
        reset();
      }
    }
    if(base == null){
      return;
    }
    writeReports(base);
  }

  private void writeReports(DiagnoseReport base) {
    JSONObject result = new JSONObject();
    JSONArray reports = new JSONArray();
    for(Fix fix: fixReportMap.keySet()){
      JSONObject report = fix.getJson();
      DiagnoseReport diagnoseReport = fixReportMap.get(fix);
      report.put("jump", diagnoseReport.getErrors().size() - base.getErrors().size());
      JSONArray errors = diagnoseReport.compare(base);
      report.put("errors", errors);
      reports.add(report);
    }
    reports.sort((o1, o2) -> {
          Integer first = (Integer) ((JSONObject) o1).get("jump");
          Integer second = (Integer) ((JSONObject) o2).get("jump");
          if (first.equals(second)) {
            return 0;
          }
          if (first < second) {
            return 1;
          }
          return -1;
        });
    result.put("reports", reports);

    try (Writer writer =
                 Files.newBufferedWriter(
                         Paths.get("/tmp/NullAwayFix/diagnose_report.json"), Charset.defaultCharset())) {
      writer.write(result.toJSONString().replace("\\/", "/").replace("\\\\\\", "\\"));
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException("Could not create the diagnose report json file");
    }
  }

  private void reset() {
    Process proc;
    try {
      proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", resetCommand});
      if (proc != null) {
        proc.waitFor();
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not run command: " + resetCommand);
    }
  }

  private void detectCommands() {
    String executablePath = AutoFix.findPathToExecutable(project.getProjectDir().getAbsolutePath(), executable);
    String task = "";
    if (!project.getPath().equals(":")) task = project.getPath() + ":";
    task += "build -x test";
    buildCommand = "cd " + executablePath + " && ./" + executable + " " + task;
    resetCommand = "cd " + executablePath + " && git reset --hard> /dev/null 2>&1";
  }

  private DiagnoseReport makeReport() {
    try {
      Process proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", buildCommand});
      String line;
      if (proc != null) {
        BufferedReader input = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        final String nullAwayErrorMessagePattern = "error:";
        List<String> errors = new ArrayList<>();
        while ((line = input.readLine()) != null) {
          if (line.contains(nullAwayErrorMessagePattern)) {
            String errorMessage = line.substring(line.indexOf("Away] ") + 6);
            errors.add(errorMessage);
          }
        }
        input.close();
        return new DiagnoseReport(errors);
      }
      return null;
    } catch (Exception e) {
      System.out.println("Error happened: " + e.getMessage());
      throw new RuntimeException("Could not run command: " + buildCommand + " from gradle");
    }
  }
}
