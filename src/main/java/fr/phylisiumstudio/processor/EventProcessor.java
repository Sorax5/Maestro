package fr.phylisiumstudio.processor;

import com.google.auto.service.AutoService;
import fr.phylisiumstudio.annotation.ActionHandler;
import fr.phylisiumstudio.annotation.Arg;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes("fr.phylisiumstudio.annotation.ActionHandler")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EventProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<String, List<ArgInfo>> eventMap = new HashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(ActionHandler.class)) {
            if (element instanceof ExecutableElement method) {
                ActionHandler handler = method.getAnnotation(ActionHandler.class);
                String eventName = handler.event();

                List<ArgInfo> args = eventMap.computeIfAbsent(eventName, k -> new ArrayList<>());

                for (Arg arg : handler.args()) {
                    String typeName = getTypeName(arg);
                    if (args.stream().noneMatch(a -> a.name().equals(arg.name()))) {
                        args.add(new ArgInfo(arg.name(), typeName));
                    }
                }
            }
        }

        if (!eventMap.isEmpty()) {
            generateEventClass(eventMap);
        }
        return true;
    }

    private void generateEventClass(Map<String, List<ArgInfo>> eventMap) {
        try {
            JavaFileObject builderFile = processingEnv.getFiler().createSourceFile("fr.phylisiumstudio.event.Events");
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                out.println("package fr.phylisiumstudio.event;");
                out.println();
                out.println("/**");
                out.println(" * GENERATED CODE - DO NOT MODIFY BY HAND");
                out.println(" */");
                out.println("public final class Events {");

                eventMap.forEach((eventName, args) -> {
                    String className = toPascalCase(eventName);
                    out.println("    public static final class " + className + " {");
                    out.println("        public static final String ID = \"" + eventName + "\";");

                    for (ArgInfo arg : args) {
                        String constantName = arg.name().toUpperCase();
                        out.printf("        public static final ArgumentKey<%s> %s = new ArgumentKey<>(\"%s\", %s.class);%n",
                                arg.type(), constantName, arg.name(), arg.type());
                    }
                    out.println("    }");
                    out.println();
                });

                out.println("}");
            }
        } catch (Exception ignored) {
        }
    }

    private String getTypeName(Arg arg) {
        try {
            return arg.type().getCanonicalName();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror().toString();
        }
    }

    private String toPascalCase(String text) {
        StringBuilder result = new StringBuilder();
        String[] parts = text.split("[._-]");
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    private record ArgInfo(String name, String type) {}
}