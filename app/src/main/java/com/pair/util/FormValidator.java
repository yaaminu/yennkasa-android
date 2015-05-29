package com.pair.util;

import android.widget.EditText;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by Null-Pointer on 5/29/2015.
 */
public class FormValidator {
    Map<EditText, ValidationStrategy> fields;


    public FormValidator() {
        this.fields = new HashMap<>();
    }

    public void addField(EditText et, ValidationStrategy strategy) {
        this.fields.put(et, strategy);
    }

    public void removeField(EditText et) {
        this.fields.remove(et);
    }

    public boolean runValidation() {
        Set<EditText> editTexts = this.fields.keySet();
        for (EditText field : editTexts) {
            ValidationStrategy strategy = this.fields.get(field);
            if ((strategy != null) && !strategy.validate(field))
                return false;
        }
        return true;
    }

    public static interface ValidationStrategy {
        boolean validate(EditText field);
    }
}
