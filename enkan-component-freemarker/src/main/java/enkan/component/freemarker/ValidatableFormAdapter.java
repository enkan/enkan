package enkan.component.freemarker;

import freemarker.ext.beans.BeanModel;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import kotowari.data.Validatable;
import org.jspecify.annotations.Nullable;

/**
 * Form object model.
 *
 * @author kawasima
 */
public class ValidatableFormAdapter extends BeanModel {
    private final TemplateMethodModelEx hasErrors;
    private final TemplateMethodModelEx getErrors;

    public ValidatableFormAdapter(Validatable form, DefaultObjectWrapper ow) {
        super(form, ow);
        this.hasErrors = arguments -> switch (arguments.size()) {
            case 0 -> (Boolean) form.hasErrors();
            case 1 -> (Boolean) form.hasErrors(arguments.getFirst().toString());
            default -> null;
        };

        this.getErrors = arguments -> switch (arguments.size()) {
            case 0 -> form.getErrors();
            case 1 -> form.getErrors(arguments.getFirst().toString());
            default -> null;
        };
    }

    public @Nullable TemplateModel get(String key) throws TemplateModelException {
        TemplateModel model = super.get(key);
        if (model == null) {
            return switch (key) {
                case "hasErrors" -> hasErrors;
                case "getErrors" -> getErrors;
                default -> null;
            };
        }
        return model;
    }
}
