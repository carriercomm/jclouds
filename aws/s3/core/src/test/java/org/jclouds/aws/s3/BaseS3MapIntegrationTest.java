/**
 *
 * Copyright (C) 2009 Global Cloud Specialists, Inc. <info@globalcloudspecialists.com>
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 */
package org.jclouds.aws.s3;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.jclouds.aws.s3.internal.BaseS3Map;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Test
public abstract class BaseS3MapIntegrationTest<T> extends S3IntegrationTest {

   public abstract void testPutAll() throws InterruptedException;

   public abstract void testEntrySet() throws IOException, InterruptedException;

   public abstract void testValues() throws IOException, InterruptedException;

   protected BaseS3Map<T> map;
   protected Map<String, String> fiveStrings = ImmutableMap.of("one", "apple", "two", "bear",
            "three", "candy", "four", "dogma", "five", "emma");

   // IMPORTANT: Java 5 struggles to correctly infer types in some cases which affects
   // this ImmutableMap. The explicit typing works around the issue. Java 6 seems to cope.
   // http://groups.google.com/group/google-collections-users/browse_thread/thread/df70c482c93a25d8
   protected Map<String, byte[]> fiveBytes = ImmutableMap.<String, byte[]> of("one",
            "apple".getBytes(), // Explicit cast necessary for Java 5
            "two", "bear".getBytes(), "three", "candy".getBytes(), "four", "dogma".getBytes(),
            "five", "emma".getBytes());
   protected Map<String, InputStream> fiveInputs;
   protected Map<String, File> fiveFiles;
   String tmpDirectory;

   @BeforeMethod(dependsOnMethods = "setUpBucket", groups = { "integration", "live" })
   @Parameters( { "basedir" })
   protected void setUpTempDir(String basedir) throws InterruptedException, ExecutionException,
            FileNotFoundException, IOException, TimeoutException {
      tmpDirectory = basedir + File.separator + "target" + File.separator + "testFiles"
               + File.separator + getClass().getSimpleName();
      new File(tmpDirectory).mkdirs();

      fiveFiles = ImmutableMap.of("one", new File(tmpDirectory, "apple"), "two", new File(
               tmpDirectory, "bear"), "three", new File(tmpDirectory, "candy"), "four", new File(
               tmpDirectory, "dogma"), "five", new File(tmpDirectory, "emma"));

      for (File file : fiveFiles.values()) {
         IOUtils.write(file.getName(), new FileOutputStream(file));
      }

      fiveInputs = ImmutableMap.of("one", IOUtils.toInputStream("apple"), "two", IOUtils
               .toInputStream("bear"), "three", IOUtils.toInputStream("candy"), "four", IOUtils
               .toInputStream("dogma"), "five", IOUtils.toInputStream("emma"));
      map = createMap(context, bucketName);
      map.clear();
   }

   protected abstract BaseS3Map<T> createMap(S3Context context, String bucket);

   @Test(groups = { "integration", "live" })
   public void testClear() throws InterruptedException {
      map.clear();
      assertEventuallyMapSize(0);
      putString("one", "apple");
      assertEventuallyMapSize(1);
      map.clear();
      assertEventuallyMapSize(0);
   }

   @Test(groups = { "integration", "live" })
   public abstract void testRemove() throws IOException, InterruptedException;

   @Test(groups = { "integration", "live" })
   public void testKeySet() throws InterruptedException {
      assertEventuallyKeySize(0);
      putString("one", "two");
      assertEventuallyKeySize(1);
      assertEventuallyKeySetEquals(ImmutableSet.of("one"));
   }

   protected void assertEventuallyKeySetEquals(final Object toEqual) throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assertEquals(map.keySet(), toEqual);
         }
      });
   }

   protected void assertEventuallyKeySize(final int size) throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assertEquals(map.keySet().size(), size);
         }
      });
   }

   @Test(groups = { "integration", "live" })
   public void testContainsKey() throws InterruptedException {
      assertEventuallyDoesntContainKey();
      putString("one", "apple");
      assertEventuallyContainsKey();
   }

   /**
    * containsValue() uses md5 comparison to bucket contents, so this can be subject to eventual
    * consistency problems.
    */
   protected void assertEventuallyContainsValue(final Object value) throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assert map.containsValue(value);
         }
      });
   }

   protected void assertEventuallyContainsKey() throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assert map.containsKey("one");
         }
      });
   }

   protected void assertEventuallyDoesntContainKey() throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assert !map.containsKey("one");
         }
      });
   }

   @Test(groups = { "integration", "live" })
   public void testIsEmpty() throws InterruptedException {
      assertEventuallyEmpty();
      putString("one", "apple");
      assertEventuallyNotEmpty();
   }

   protected void assertEventuallyNotEmpty() throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assert !map.isEmpty();
         }
      });
   }

   protected void assertEventuallyEmpty() throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assert map.isEmpty();
         }
      });
   }

   abstract protected void putString(String key, String value);

   protected void fourLeftRemovingOne() throws InterruptedException {
      map.remove("one");
      assertEventuallyMapSize(4);
      assertEventuallyKeySetEquals(new TreeSet<String>(ImmutableSet.of("two", "three", "four",
               "five")));
   }

   protected void assertEventuallyMapSize(final int size) throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assertEquals(map.size(), size);
         }
      });
   }

   @Test(groups = { "integration", "live" })
   public abstract void testPut() throws IOException, InterruptedException;

   @Test(groups = { "integration", "live" })
   public void testGetBucket() throws InterruptedException {
      assertEventuallyBucketNameCorrect();
   }

   protected void assertEventuallyBucketNameCorrect() throws InterruptedException {
      assertEventually(new Runnable() {
         public void run() {
            assertEquals(map.getBucket().getName(), bucketName);
         }
      });
   }

}