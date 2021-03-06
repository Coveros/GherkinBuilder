/*
 * Copyright 2018 Coveros, Inc.
 *
 * This file is part of Gherkin Builder.
 *
 * Gherkin Builder is licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.coveros;

import com.coveros.exception.MalformedGlueCode;
import com.coveros.exception.MalformedMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlueCode {

    private Logger log = Logger.getLogger("GherkinBuilder");

    private EnumInfo enumInfo = new EnumInfo(null);
    private Boolean next = false;
    private List<String> steps;
    private List<String> baseDirectories = new ArrayList<>();
    private StringBuilder step;

    public GlueCode() {
        steps = new ArrayList<>();
        step = new StringBuilder();
    }

    public void addBaseDirectory(String baseDirectory) {
        baseDirectories.add(baseDirectory);
        enumInfo = new EnumInfo(baseDirectories);
    }

    /**
     * Runs through the provided line, and determines how to parse it
     *
     * @param line - a provided line from a Cucumber Glue Code path
     * @return String - a step to be consumed by the gherkin builder class as js
     */
    public void processLine(String line) throws IOException {
        // grab any imports that might be useful
        if (line.startsWith("import ")) {
            enumInfo.addClassInclude(line.substring(7, line.length() - 1));
        }
        // if our previous line was just a Given, When or Then, next
        // will be set, to indicate this line contains parameters
        if (next) {
            step.append(getStepVariables(getMethodVariables(line)));
            step.append(" ) );");
            next = false;
            steps.add(step.toString());
            step.setLength(0);
        }
        String ln = line.trim();
        if (ln.startsWith("@Given") || ln.startsWith("@When") || ln.startsWith("@Then")) {
            step.append("testSteps.push( new step( \"" + getStep(ln) + "\"");
            next = true;
        }
    }

    /**
     * Extracts the regular expression from the cucumber given, when or then
     * annotation
     *
     * @param glueCode - cucumber given, when or then annotation. Example:
     *                 \@Given("^I have a new registered user$") \@When("^I
     *                 (.*)login$") \@Then("^I see the login error message
     *                 \"([^\"]*)\"$")
     * @return String - a formatted string to be consumer by the gherkin builder
     * @throws MalformedGlueCode
     */
    public String getStep(String glueCode) throws MalformedGlueCode {
        // check for valid formatted glue code
        int start = glueCode.indexOf('^');
        int end = glueCode.lastIndexOf('$');
        if (start < 0 || end < 0 || start > end) {
            String error = "There is a problem with your glue code. It is expected to" +
                    " start with '^' and end with '$'. Examine the expression '" + glueCode + "'";
            log.log(Level.SEVERE, error);
            throw new MalformedGlueCode(error);
        }
        // get just the regex from the annotation
        String regex = glueCode.substring(start + 1, end);
        // denote a non-capturing match
        regex = regex.replaceAll("\\(\\?:.*?\\)", "<span class='any'>...</span>");
        // capture any generic matches
        regex = regex.replaceAll("\\(.*?\\)", "XXXX");
        // capture any optional matches
        regex = regex.replaceAll("\\[(.*?)\\]\\?", "<span class='opt'>$1</span>");
        return regex;
    }

    /**
     * Retrieves the list of parameters from a given method declaration
     *
     * @param method - the string representation of a method
     * @return List - a list of paramters from the method, as strings. It will
     * list object, then the object name
     * @throws MalformedMethod
     */
    public List<String> getMethodVariables(String method) throws MalformedMethod {
        // check for valid formatted java method
        int start = method.indexOf('(');
        int end = method.lastIndexOf(')');
        if (start < 0 || end < 0 || start > end) {
            String error = "There is a problem with your method declaration. It does not contain" +
                    " a proper parameter definition. Examine the declaration '" + method + "'";
            log.log(Level.SEVERE, error);
            throw new MalformedMethod(error);
        }
        // define where our parameters are captured
        String params = method.substring(start + 1, end);
        // if any parameters exist
        if (params.length() > 0) {
            // grab an array of them
            return Arrays.asList(params.split(","));
        }
        // if no parameters, ok to return empty
        return new ArrayList<>();
    }

    /**
     * Determines if the provided string is a list or not, with a specific
     * object typed
     *
     * @param input - a string interpretation of an object
     * @return Boolean - is it a properly identified list
     */
    public Boolean isList(String input) {
        return input.startsWith("List<") && input.endsWith(">") && input.length() > 6;
    }

    /**
     * Determines if the provided string is a text element or not. A text
     * element is considered a string, character, double or boolean
     *
     * @param input - a string interpretation of an object
     * @return Boolean - is it a text element
     */
    public Boolean isText(String input) {
        return "string".equalsIgnoreCase(input) || "char".equalsIgnoreCase(input) || "double".equalsIgnoreCase(input) ||
                "boolean".equalsIgnoreCase(input) || "datatable".equalsIgnoreCase(input);
    }

    /**
     * Determines if the provided string is a number element or not. A number
     * element is considered a long, or int
     *
     * @param input - a string interpretation of an object
     * @return Boolean - is it a number element
     */
    public Boolean isNumber(String input) {
        return "long".equalsIgnoreCase(input) || "int".equalsIgnoreCase(input) || "integer".equalsIgnoreCase(input);
    }

    /**
     * Takes a list of paramters and converts it into step variables
     *
     * @param parameters - a list of paramters, as strings. It should list object, then
     *                   the object name
     * @return String - a keypair definition to be added to the step definition
     */
    public String getStepVariables(List<String> parameters) {
        StringBuilder params = new StringBuilder();
        for (String parameter : parameters) {
            // remove any surrounding whitespace
            parameter = parameter.trim();
            if (parameter.startsWith("@Transform")) {
                parameter = parameter.split("\\) ")[1];
            }
            if (parameter.startsWith("@Delimiter")) {
                parameter = parameter.split("\\) ")[1];
            }
            String type;
            String object = parameter.split(" ")[0];
            String name = parameter.split(" ")[1];

            // are we dealing with a list of elements
            if (isList(object)) {
                object = object.substring(5, object.length() - 1);
                name += "List";
            }
            // are we dealing with a whole number
            if (isNumber(object)) {
                type = "\"number\"";
            } else if (isText(object)) {
                type = "\"text\"";
            } else if ("date".equalsIgnoreCase(object)) {
                type = "\"date\"";
            } else {
                type = object;
                enumInfo.addGlueCodeEnumeration(type);
            }
            params.append(", new keypair( \"" + name + "\", " + type + " )");
        }
        return params.toString();
    }

    /**
     * Returns the identified steps while parsing through the method parameters
     *
     * @return List - a list of steps
     */
    public List<String> getGlueCodeSteps() {
        return steps;
    }

    /**
     * Returns the enumerations identified in the step code
     *
     * @return EnumInfo - all of the enumerations identified
     */
    public EnumInfo getEnumInfo() {
        return enumInfo;
    }
}