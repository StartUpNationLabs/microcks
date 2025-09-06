/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util.el.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for RandomStringELFunction class.
 * @author laurent
 */
class RandomStringELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      // Compute evaluation.
      RandomStringELFunction function = new RandomStringELFunction();
      String result = function.evaluate(null);

      assertEquals(RandomStringELFunction.DEFAULT_LENGTH, result.length());
   }

   @Test
   void testCustomSizeEvaluation() {
      // Compute evaluation.
      RandomStringELFunction function = new RandomStringELFunction();
      String result = function.evaluate(null, "64");

      assertEquals(64, result.length());
   }

   @Test
   void testMinMaxLengthEvaluation() {
      RandomStringELFunction function = new RandomStringELFunction();
      String result = function.evaluate(null, "5", "10");
      assertTrue(result.length() >= 5 && result.length() <= 10);
   }

   @Test
   void testMinMaxSwapAndEquality() {
      RandomStringELFunction function = new RandomStringELFunction();
      // swapped bounds should be handled
      String swapped = function.evaluate(null, "10", "5");
      assertTrue(swapped.length() >= 5 && swapped.length() <= 10);

      // equal bounds should produce fixed length
      String fixed = function.evaluate(null, "7", "7");
      assertEquals(7, fixed.length());
   }
}
