/**
 * Asking a plugin how it is, and answering in one line per thing that could be wrong.
 *
 * <p>Every plugin reads its content at boot and writes what it could not read to the console. An operator
 * who was not watching, or who is not the person with console access, has no way to ask again. These four
 * types are the shape of the answer: a check with a name, a result with a severity and a sentence, and a
 * report that folds them. What is checked is each plugin's own business and none of it is here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.health;
