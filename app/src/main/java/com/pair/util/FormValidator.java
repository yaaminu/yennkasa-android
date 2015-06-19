package com.pair.util;

import android.widget.EditText;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
@SuppressWarnings("unused")
public class FormValidator {
    Map<ValidationStrategy,Runnable> fields;


    public FormValidator() {
        this.fields = new HashMap<>();
    }

    public void addStrategy(ValidationStrategy strategy, Runnable action) {
        this.fields.put(strategy,action);
    }

    public void dismissStrategy(ValidationStrategy strategy) {
        //noinspection SuspiciousMethodCalls
        this.fields.remove(strategy);
    }

    public boolean runValidation() {
        Set<ValidationStrategy> strategies = this.fields.keySet();
        for (ValidationStrategy strategy : strategies) {
            if (!strategy.validate()){
                // On Android callers must make sure they run validation on main thread
                //if they want to tamper with the views
                Runnable action = this.fields.get(strategy);
                if(action != null){
                    action.run();
                }
                return false;
            }
        }
        return true;
    }

    public  interface ValidationStrategy {
        boolean validate();
    }

    public  final class EditTextValidationStrategy  implements ValidationStrategy{
        private final EditText textField;
        private final Pattern pattern;
        public EditTextValidationStrategy(EditText editText,Pattern regExp){
            this.textField = editText;
            this.pattern = regExp;
        }
        @Override
        public boolean validate() {
            //TODO make use of the passed pattern
            String fieldContent = UiHelpers.getFieldContent(textField);
            Matcher matcher = pattern.matcher(fieldContent);
            return matcher.matches();
        }
    }

    //TEXT_FIELD_NOT_EMPTY
}
