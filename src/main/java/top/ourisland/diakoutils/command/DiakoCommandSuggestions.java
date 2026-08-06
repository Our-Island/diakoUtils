package top.ourisland.diakoutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import top.ourisland.diakoutils.DiakoUtils;

import java.util.LinkedHashSet;

final class DiakoCommandSuggestions {

    static final SuggestionProvider<CommandSourceStack> MODULE_IDS = (_, builder) ->
            SharedSuggestionProvider.suggest(DiakoUtils.MODULES.ids(), builder);

    static final SuggestionProvider<CommandSourceStack> PROPERTY_IDS = (context, builder) -> {
        var moduleId = StringArgumentType.getString(context, "module");
        return SharedSuggestionProvider.suggest(DiakoUtils.PROPERTIES.ids(moduleId), builder);
    };

    static final SuggestionProvider<CommandSourceStack> PROPERTY_VALUES = (context, builder) -> {
        var moduleId = StringArgumentType.getString(context, "module");
        var propertyId = StringArgumentType.getString(context, "property");
        var module = DiakoUtils.MODULES.get(moduleId);
        var descriptor = DiakoUtils.PROPERTIES.get(moduleId, propertyId);

        if (module == null || descriptor == null) {
            return builder.buildFuture();
        }

        var suggestions = new LinkedHashSet<>(descriptor.suggestions());
        suggestions.add(DiakoCommandComponents.format(
                descriptor,
                DiakoCommandComponents.get(descriptor, module)
        ));
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    private DiakoCommandSuggestions() {
    }

}
