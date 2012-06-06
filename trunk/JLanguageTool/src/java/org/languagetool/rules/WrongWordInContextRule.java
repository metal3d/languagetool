/* LanguageTool, a natural language style checker 
 * Copyright (C) 2012 Markus Brenneis
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.Category;
import org.languagetool.rules.RuleMatch;
import org.languagetool.tools.StringTools;

/**
 * Check if there is a confusion of two words (which might have a similar spelling) depending on the context.
 *  This rule loads the words and their respective context from external files.
 * 
 * @author Markus Brenneis
 */
public abstract class WrongWordInContextRule extends Rule {

  private final List<ContextWords> contextWordsSet;

  public WrongWordInContextRule(final ResourceBundle messages) throws IOException {
    if (messages != null) {
      super.setCategory(new Category(getCategoryString()));
    }
    final String filename = getFilename();
    contextWordsSet = loadContextWords(JLanguageTool.getDataBroker().getFromRulesDirAsStream(filename));
  }

  protected abstract String getFilename();

  protected String getCategoryString() {
    return messages.getString("category_misc");
  }
  
  @Override
  public String getId() {
    return "WRONG_WORD_IN_CONTEXT";
  }

  @Override
  public String getDescription() {
    return "Confusion of words";
  }

  @Override
  public RuleMatch[] match(final AnalyzedSentence text) {
    final List<RuleMatch> ruleMatches = new ArrayList<RuleMatch>();
    final AnalyzedTokenReadings[] tokens = text.getTokensWithoutWhitespace();
    for (ContextWords contextWords: contextWordsSet) {
      final boolean[] matchedWord = {false, false};
      final Matcher[] matchers = {null, null};
      matchers[0] = contextWords.words[0].matcher("");
      matchers[1] = contextWords.words[1].matcher("");
      //start searching for words
      //ignoring token 0, i.e., SENT_START
      int i;
      String token1 = "";
      for (i = 1; i < tokens.length && !matchedWord[0]; i++) {
        token1 = tokens[i].getToken();
        matchedWord[0] = matchers[0].reset(token1).find();
      }
      int j;
      String token2 = "";
      for (j = 1; j < tokens.length && !matchedWord[1]; j++) {
        token2 = tokens[j].getToken();
        matchedWord[1] = matchers[1].reset(token2).find();
      }
      
      int foundWord = -1;
      int notFoundWord = -1;
      int startPos = 0;
      int endPos = 0;
      String matchedToken = "";
      // when both words have been found, we cannot determine if one of them is wrong
      if(matchedWord[0] && !matchedWord[1]) {
        foundWord = 0;
        notFoundWord = 1;
        matchers[1] = contextWords.contexts[1].matcher("");
        startPos = tokens[i-1].getStartPos();
        endPos = tokens[i-1].getStartPos() + token1.length();
        matchedToken = token1;
      } else if(matchedWord[1] && !matchedWord[0]) {
        foundWord = 1;
        notFoundWord = 0;
        matchers[0] = contextWords.contexts[0].matcher("");
        startPos = tokens[j-1].getStartPos();
        endPos = tokens[j-1].getStartPos() + token2.length();
        matchedToken = token2;
      }
      
      if(foundWord != -1) {
        final boolean[] matchedContext = {false, false};
        matchers[foundWord] = contextWords.contexts[foundWord].matcher("");
        matchers[notFoundWord] = contextWords.contexts[notFoundWord].matcher("");
        //start searching for context words
        //ignoring token 0, i.e., SENT_START
        String token = "";
        for (i = 1; i < tokens.length && !matchedContext[foundWord]; i++) {
          token = tokens[i].getToken();
          matchedContext[foundWord] = matchers[foundWord].reset(token).find();
        }
        for (i = 1; i < tokens.length && !matchedContext[notFoundWord]; i++) {
          token = tokens[i].getToken();
          matchedContext[notFoundWord] = matchers[notFoundWord].reset(token).find();
        }
        if(matchedContext[notFoundWord] && !matchedContext[foundWord]) {
          final String msg = getMessage(matchedToken, matchedToken.replaceFirst(contextWords.matches[foundWord],contextWords.matches[notFoundWord]),
                  contextWords.explanations[notFoundWord], contextWords.explanations[foundWord]);
          final String shortMsg = getShortMessage(matchedToken.replaceFirst(contextWords.matches[foundWord],contextWords.matches[notFoundWord]));
          final RuleMatch ruleMatch = new RuleMatch(this, startPos, endPos, msg, shortMsg);
          ruleMatches.add(ruleMatch);
        }
      } // if foundWord != -1
    } // for each contextWords in contextWordsSet
    return toRuleMatchArray(ruleMatches);
  }
  
  /**
   * @return a string like "Possible confusion of words: Did you mean <suggestion>$SUGGESTION</suggestion> instead of '$WRONGWORD'?"
   */
  protected abstract String getMessageString();
  
  /**
   * @return a string like "Possible confusion of words"
   */
  protected abstract String getShortMessageString();
  
  /**
   * @return a string like "Possible confusion of words: Did you mean <suggestion>$SUGGESTION</suggestion>
   * (= $EXPLANATION_SUGGESTION) instead of '$WRONGWORD' (= $EXPLANATION_WRONGWORD)?"
   */
  protected abstract String getLongMessageString();
  
  private String getMessage(String wrongWord, String suggestion, String explanationSuggestion, String explanationWrongWord) {
    if (explanationSuggestion.equals("") || explanationWrongWord.equals("")) {
      return getMessageString().replaceFirst("\\$SUGGESTION", suggestion).replaceFirst("\\$WRONGWORD", wrongWord);
    } else {
      return getLongMessageString().replaceFirst("\\$SUGGESTION", suggestion).replaceFirst("\\$WRONGWORD", wrongWord)
              .replaceFirst("\\$EXPLANATION_SUGGESTION", explanationSuggestion).replaceFirst("\\$EXPLANATION_WRONGWORD", explanationWrongWord);
    }
  }
  
  private String getShortMessage(String suggestion) {
    return getShortMessageString();
  }
  
  /**
   * Load words, contexts, and explanations.
   */
  private List<ContextWords> loadContextWords(final InputStream file) throws IOException {
    final List<ContextWords> set = new Vector<ContextWords>();
    final Scanner scanner = new Scanner(file, "utf-8");
    try {
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine();
        if (line.charAt(0) == '#') {
          continue;
        }
        final String[] column = line.split("\t");
        if (column.length >= 6) {
          final ContextWords contextWords = new ContextWords();
          contextWords.setWord(0, column[0]);
          contextWords.setWord(1, column[1]);
          contextWords.matches[0] = column[2];
          contextWords.matches[1] = column[3];
          contextWords.setContext(0, column[4]);
          contextWords.setContext(1, column[5]);
          if (column.length > 6) {
            contextWords.explanations[0] = column[6];
            if (column.length > 7) {
              contextWords.explanations[1] = column[7];
            }
          }
          set.add(contextWords);
        } // if (column.length >= 6)
      }
    } finally {
      scanner.close();
    }
    return set;
  }
  
  class ContextWords {
    
    public String matches[] = {"", ""}, explanations[] = {"", ""};
    public Pattern words[], contexts[];
    
    ContextWords() {
      words = new Pattern[2];
      contexts = new Pattern[2];
    }
    
    public void setWord(int i, String word) {
      words[i] = Pattern.compile(word);
    }
    
    public void setContext(int i, String context) {
      contexts[i] = Pattern.compile(context);
    }
    
  }

  @Override
  public void reset() {
    // nothing
  }

}