package edu.ucr.cs.riple.autofixer;

import edu.ucr.cs.riple.autofixer.metadata.CallGraph;
import edu.ucr.cs.riple.autofixer.metadata.MethodInheritanceTree;
import edu.ucr.cs.riple.autofixer.nullaway.FixDisplay;
import edu.ucr.cs.riple.injector.Fix;
import edu.ucr.cs.riple.injector.Injector;
import edu.ucr.cs.riple.injector.WorkList;
import edu.ucr.cs.riple.injector.WorkListBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Diagnose {

  Injector injector;
  String buildCommand;
  Map<Fix, DiagnoseReport> fixReportMap;
  String fixPath;
  String diagnosePath;
  MethodInheritanceTree methodInheritanceTree;
  CallGraph callGraph;

  private static void executeCommand(String command) {
    try {
      System.out.println("Executing command: " + command);
      Process p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});
      System.out.println("Requested");
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      while ((reader.readLine()) != null) {}
      p.waitFor();
      System.out.println("Finished.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void start(String buildCommand, String out_dir, boolean optimized) {
    this.buildCommand = buildCommand;
    this.fixPath = out_dir + "/fixes.csv";
    this.diagnosePath = out_dir + "/diagnose.json";
    methodInheritanceTree = new MethodInheritanceTree(out_dir + "/method_info.csv");
    callGraph = new CallGraph(out_dir + "/call_graph.csv");
    System.out.println("Diagnose Started...");
    injector = Injector.builder().setMode(Injector.MODE.BATCH).build();
    System.out.println("Starting preparation");
    prepare(out_dir, optimized);
    System.out.println("Build command: " + buildCommand);
    fixReportMap = new HashMap<>();
    List<WorkList> workListLists = new WorkListBuilder(diagnosePath).getWorkLists();
    DiagnoseReport base = makeReport();
    try {
      for (WorkList workList : workListLists) {
        for (Fix fix : workList.getFixes()) {
          List<Fix> appliedFixes = analyze(fix);
          remove(appliedFixes);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    writeReports(base);
  }

  private void remove(List<Fix> fixes) {
    List<Fix> toRemove = new ArrayList<>();
    for(Fix fix: fixes){
      Fix removeFix = new Fix(fix.annotation, fix.method, fix.param, fix.location, fix.className, fix.pkg, fix.uri, "false");
      toRemove.add(removeFix);
    }
    injector.start(Collections.singletonList(new WorkList(toRemove)));
  }

  private List<Fix> analyze(Fix fix) {
    List<Fix> suggestedFix = new ArrayList<>();
    suggestedFix.add(fix);
//    if(fix.location.equals("METHOD_PARAM")) {
//      List<MethodInfo> subMethods = methodInheritanceTree.getSubMethods(fix.method, fix.className);
//      for (MethodInfo info : subMethods) {
//        suggestedFix.add(new Fix(
//                fix.annotation,
//                info.method,
//                fix.param,
//                fix.location,
//                info.clazz,
//                fix.pkg,
//                info.uri,
//                fix.inject
//        ));
//      }
//    }
//    if(fix.location.equals("METHOD_RETURN")) {
//      List<MethodInfo> subMethods = methodInheritanceTree.getSuperMethods(fix.method, fix.className);
//      for (MethodInfo info : subMethods) {
//        suggestedFix.add(new Fix(
//                fix.annotation,
//                info.method,
//                fix.param,
//                fix.location,
//                info.clazz,
//                fix.pkg,
//                info.uri,
//                fix.inject
//        ));
//      }
//    }
    injector.start(Collections.singletonList(new WorkList(suggestedFix)));
    fixReportMap.put(fix, makeReport());
    return suggestedFix;
  }

  @SuppressWarnings("Unchecked")
  private void prepare(String out_dir, boolean optimized) {
    try {
      System.out.println("Preparing project: with optimization flag:" + optimized);
      executeCommand(buildCommand);
      if(! new File(fixPath).exists()){
        JSONObject toDiagnose = new JSONObject();
        toDiagnose.put("fixes", new JSONArray());
        FileWriter writer = new FileWriter(diagnosePath);
        writer.write(toDiagnose.toJSONString());
        writer.flush();
        System.out.println("No new fixes from NullAway, created empty list.");
        return;
      }
      new File(diagnosePath).delete();
      convertCSVToJSON(out_dir + "/fixes.csv", out_dir + "/fixes.json");
      System.out.println("Deleted old diagnose file.");
      System.out.println("Making new diagnose.json.");
      if(!optimized){
        executeCommand("cp " + fixPath + " " + diagnosePath);
        convertCSVToJSON(diagnosePath, diagnosePath);
      }else{
        try{
          System.out.println("Removing already diagnosed fixes...");
          Object obj = new JSONParser().parse(new FileReader(fixPath));
          JSONObject fixes = (JSONObject) obj;
          obj = new JSONParser().parse(new FileReader(out_dir + "/diagnosed.json"));
          JSONObject diagnosed = (JSONObject) obj;
          JSONArray fixes_array = (JSONArray) fixes.get("fixes");
          JSONArray diagnosed_array = (JSONArray) diagnosed.get("fixes");
          fixes_array.removeAll(diagnosed_array);
          JSONObject toDiagnose = new JSONObject();
          toDiagnose.put("fixes", fixes_array);
          FileWriter writer = new FileWriter(diagnosePath);
          writer.write(toDiagnose.toJSONString());
          writer.flush();
        }catch (RuntimeException exception){
          System.out.println("Exception happened while optimizing suggested fixes.");
          System.out.println("Continuing...");
          executeCommand("cp " + fixPath + " " + diagnosePath);
        }
      }
      System.out.println("Made.");
      System.out.println("Preparation done");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void convertCSVToJSON(String csvPath, String jsonPath) {
    JSONArray fixes = new JSONArray();
    BufferedReader reader;
    FileWriter writer;
    try {
      reader = Files.newBufferedReader(Paths.get(csvPath), Charset.defaultCharset());
      String line = reader.readLine();
      if(line != null) line = reader.readLine();
      while (line != null) {
        FixDisplay fix = FixDisplay.fromCSVLine(line);
        fixes.add(fix.getJson());
        line = reader.readLine();
      }
      reader.close();
      JSONObject res = new JSONObject();
      JSONArray fixesArray = new JSONArray();
      fixesArray.addAll(fixes);
      res.put("fixes", fixesArray);
      writer = new FileWriter(jsonPath);
      writer.write(res.toJSONString().replace("\\/", "/").replace("\\\\\\", "\\"));
      writer.flush();
    } catch (IOException e) {
      System.err.println("Error happened in converting csv to json!");
    }
  }

  private void writeReports(DiagnoseReport base) {
    JSONObject result = new JSONObject();
    JSONArray reports = new JSONArray();
    for (Fix fix : fixReportMap.keySet()) {
      JSONObject report = fix.getJson();
      DiagnoseReport diagnoseReport = fixReportMap.get(fix);
      report.put("jump", diagnoseReport.getErrors().size() - base.getErrors().size());
      JSONArray errors = diagnoseReport.compare(base);
      report.put("errors", errors);
      reports.add(report);
    }
    reports.sort(
        (o1, o2) -> {
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
    try  {
      FileWriter writer = new FileWriter("/tmp/NullAwayFix/diagnose_report.json");
      writer.write(result.toJSONString().replace("\\/", "/").replace("\\\\\\", "\\"));
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException("Could not create the diagnose report json file");
    }
  }

  private DiagnoseReport makeReport() {
    try {
      executeCommand(buildCommand);
      File tempFile = new File("/tmp/NullAwayFix/errors.json");
      boolean exists = tempFile.exists();
      if(exists){
        Object obj = new JSONParser().parse(new FileReader("/tmp/NullAwayFix/errors.json"));
        return new DiagnoseReport((JSONObject)obj);
      }
      return DiagnoseReport.empty();
    } catch (Exception e) {
      System.out.println("Error happened: " + e.getMessage());
      throw new RuntimeException("Could not run command: " + buildCommand);
    }
  }
}

