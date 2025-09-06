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

import io.github.microcks.util.el.EvaluationContext;

/**
 * This is an implementation of ELFunction that generates random integer values. When invoked with no arg a integer
 * between <code>Integer.MIN_VALUE</code> and <code>Integer.MAX_VALUE</code> is generated. When invoked with one
 * argument, a positive integer bounded by arg value is generated. When invoked with 2 arguments, an integer in
 * designated intervfal is generated.
 * @author laurent
 */
public class RandomIntELFunction extends AbstractRandomELFunction {

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      if (args != null) {
         switch (args.length) {
            case 1:
               int maxValue = Integer.MAX_VALUE;
               try {
                  maxValue = Integer.parseInt(args[0]);
               } catch (NumberFormatException nfe) {
                  // Ignore, we'll stick to integer max value.
               }
               // 0 (inclusive) to maxValue (exclusive)
               return String.valueOf(getRandom().nextInt(maxValue));
            case 2:
               int minValue = 0;
               maxValue = Integer.MAX_VALUE;
               try {
                  minValue = Integer.parseInt(args[0]);
                  maxValue = Integer.parseInt(args[1]);
               } catch (NumberFormatException nfe) {
                  // Ignore, we'll stick to the defaults.
               }
               if (maxValue < minValue) {
                  int tmp = maxValue; maxValue = minValue; minValue = tmp;
               }
               if (maxValue == minValue) {
                  return String.valueOf(minValue);
               }
               return String.valueOf(getRandom().nextInt(maxValue - minValue) + minValue);
            case 3:
               // min, max, inclusiveUpper(boolean)
               minValue = 0;
               maxValue = Integer.MAX_VALUE;
               boolean inclusiveUpper = false;
               try {
                  minValue = Integer.parseInt(args[0]);
                  maxValue = Integer.parseInt(args[1]);
               } catch (NumberFormatException nfe) {
                  // keep defaults
               }
               try {
                  inclusiveUpper = Boolean.parseBoolean(args[2]);
               } catch (Exception ignored) {}
               if (maxValue < minValue) {
                  int tmp = maxValue; maxValue = minValue; minValue = tmp;
               }
               if (inclusiveUpper) {
                  if (maxValue == Integer.MAX_VALUE) {
                     // avoid overflow when adding 1
                     return String.valueOf(getRandom().nextInt(Integer.MAX_VALUE - minValue) + minValue);
                  }
                  maxValue = maxValue + 1;
               }
               if (maxValue == minValue) {
                  return String.valueOf(minValue);
               }
               return String.valueOf(getRandom().nextInt(maxValue - minValue) + minValue);
            default:
               return String.valueOf(getRandom().nextInt());
         }
      }
      return String.valueOf(getRandom().nextInt());
   }
}
