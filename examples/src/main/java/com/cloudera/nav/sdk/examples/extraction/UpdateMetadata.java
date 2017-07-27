package com.cloudera.nav.sdk.examples.extraction;

import com.cloudera.nav.sdk.client.ClientConfig;
import com.cloudera.nav.sdk.client.ClientConfigFactory;
import com.cloudera.nav.sdk.client.EntityAttrs;
import com.cloudera.nav.sdk.client.EntityUpdateAttrs;
import com.cloudera.nav.sdk.client.NavApiCient;
import com.cloudera.nav.sdk.model.Source;
import com.cloudera.nav.sdk.model.entities.EntityType;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opencsv.CSVReader;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * This program imports metadata from a CSV file and applies to the items entities in Navigator.
 * The program takes two arguments: The config file and the CSV file.
 * A sample CSV file is available in sample_metadata.csv. It has the following columns
 *        SoureName, Type, ParentPath, originalName, Name, Description, tags, CM.prop1, MM.ims.MM
 * All custom properties should appear before managed metadata
 */
public class UpdateMetadata {
  private static final   Logger LOG =
      LoggerFactory.getLogger(UpdateMetadata.class);
  private static final int CUSTOM_METADATA_START_INDEX = 7;

  private static Map<Integer, String> customMetadataPropNames = Maps.newHashMap();
  private static Map<Integer, String> managedMetadataNameSpaces = Maps.newHashMap();
  private static Map<Integer, String> managedMetadataPropNames = Maps.newHashMap();

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws IOException {
    LogManager.getRootLogger().setLevel(Level.INFO);

    String configFilePath = args[0];
    ClientConfig config = (new ClientConfigFactory())
        .readConfigurations(configFilePath);


    // Initialize the API.
    NavApiCient client = new NavApiCient(config);

    Collection<Source> sources = client.getAllSources();
    Map<String, Source> sourceMap = Maps.newHashMap();
    for(Source source : sources) {
      sourceMap.put(source.getName().toUpperCase(), source);
    }

    CSVReader reader =  new CSVReader(new FileReader(args[1]));

    // Read the header and get column names.
    // The format should be: SoureId, Type, ParentPath, originalName, Name, Description
    parseHeaders(reader.readNext()); // Skip the headers

    String[] line;
    while((line = reader.readNext()) != null) {
      int index = 0;
      String sourceName = getNextCol(line, index++).toUpperCase();
      String sourceId = sourceMap.get(sourceName).getIdentity();

      EntityType type = EntityType.valueOf(getNextCol(line, index++));
      String parentPath = getNextCol(line, index++);
      String originalName = getNextCol(line, index++);

      EntityAttrs entity = client.findEntity(sourceId, parentPath, originalName, type);
      if (entity == null) {
        LOG.info("Cannot find entity: {}", entity);
        continue;
      }

      EntityUpdateAttrs updateAttrs = new EntityUpdateAttrs();
      updateAttrs.setName(getNextCol(line, index++));
      updateAttrs.setDescription(getNextCol(line, index++));
      updateAttrs.setTags(parseTags(getNextCol(line, index++)));

      updateCustomProperties(updateAttrs, line);
      updateManagedProperties(updateAttrs, line);

      try {
        entity = client.updateEntity(entity.getIdentity(), updateAttrs);
        LOG.info("Updated entity {}", entity.getIdentity());
      } catch (Exception e) {
        LOG.info("Unable to update element: {}", entity, e);
      }
    }
    reader.close();
  }


  private static void parseHeaders(String[] line) {
    // Headers look like this
    // SoureName, Type, ParentPath, originalName, Name, Description, tags, mm_prop1, cm_prop1

    // Custom metadata starts at index = 7.
    if (line.length == CUSTOM_METADATA_START_INDEX) {
      return;
    }

    int i;
    for(i = CUSTOM_METADATA_START_INDEX; i < line.length; i++) {
      String header = line[i].trim();
      if (!header.startsWith("CM.")) {
        break;
      }
      customMetadataPropNames.put(i, header.substring(3));
    }

    Map<String, Map<String, String>> properties = Maps.newHashMap();
    for(; i < line.length; i++) {
      String header = line[i].trim();
      if (!header.startsWith("MM.")) {
        break;
      }

      //TODO: This code will have a problem if the managed metadata property name has a . in it.
      // Pattern should be MM.MyNameSpace.PropName
      String[] split = header.split("\\.");
      managedMetadataNameSpaces.put(i, split[1]);
      managedMetadataPropNames.put(i, split[2]);
    }
  }

  private static void updateCustomProperties(EntityUpdateAttrs updateAttrs, String[] line) {
    if (customMetadataPropNames.isEmpty()) {
      return;
    }

    Map<String, String> properties = Maps.newHashMap();

    // Go through all the custom properties first
    for(Map.Entry<Integer, String> entry : customMetadataPropNames.entrySet()) {
      properties.put(entry.getValue(), line[entry.getKey()].trim());
    }
    updateAttrs.setProperties(properties);
  }

  private static void updateManagedProperties(EntityUpdateAttrs updateAttrs, String[] line) {
    if (managedMetadataNameSpaces.isEmpty()) {
      return;
    }

    Map<String, Map<String, Object>> properties = Maps.newHashMap();

    for(Map.Entry<Integer, String> entry : managedMetadataNameSpaces.entrySet()) {
      String nameSpace = entry.getValue();
      Map<String, Object> namespaceVals = properties.get(nameSpace);
      if (namespaceVals == null) {
        namespaceVals = Maps.newHashMap();
        properties.put(nameSpace, namespaceVals);
      }

      String propName = managedMetadataPropNames.get(entry.getKey());
      namespaceVals.put(propName, line[entry.getKey()].trim());
    }
    updateAttrs.setCustomProperties(properties);
  }

  private static String getNextCol(String[] line, int i) {
    return line[i].trim();
  }

  private static Set<String> parseTags(String col) {
    String[] tags = col.split(",");
    Set<String> ret = Sets.newHashSetWithExpectedSize(tags.length);
    for(String tag : tags) {
        ret.add(tag.trim());
    }
    return ret;
  }
}