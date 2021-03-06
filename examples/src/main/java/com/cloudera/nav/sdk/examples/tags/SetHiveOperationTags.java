// (c) Copyright 2018 Cloudera, Inc. All rights reserved.
package com.cloudera.nav.sdk.examples.tags;

import com.cloudera.nav.sdk.client.NavApiCient;
import com.cloudera.nav.sdk.client.NavigatorPlugin;
import com.cloudera.nav.sdk.client.writer.ResultSet;
import com.cloudera.nav.sdk.model.Source;
import com.cloudera.nav.sdk.model.entities.HiveColumn;
import com.cloudera.nav.sdk.model.entities.HiveOperation;
import com.google.common.collect.Sets;

public class SetHiveOperationTags {
  public static void main(String[] args) {

    // setup the plugin and api client
    NavigatorPlugin plugin = NavigatorPlugin.fromConfigFile(args[0]);

    // send tags for multiple entities to Navigator
    HiveOperation operation = new HiveOperation();
    operation.setQueryText("select description from sample_08 where salary > " +
        "20");
    operation.setTags(Sets.newHashSet("YOLO",
        "TAG2"));

    NavApiCient client = plugin.getClient();
    Source hiveSource = client.getHMSSource();
    operation.setSourceId(hiveSource.getIdentity());

    // Write metadata\
    ResultSet results = plugin.write(operation);

    if (results.hasErrors()) {
      throw new RuntimeException(results.toString());
    }
  }
}
