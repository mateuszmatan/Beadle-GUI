package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptions;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptions.TypeOption;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptionsUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type;
import com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question;

import java.util.LinkedHashMap;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.CATEGORIES;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@UseCase
public class ChangeOptionsService implements ChangeOptionsUseCase {

    @Override
    @WithoutTransaction
    public ChangeOptions options() {
        return new ChangeOptions(CATEGORIES,
                stream(Type.values()).map(type -> new TypeOption(type.name(), type.label())).toList(),
                stream(Question.values()).collect(toMap(Question::field, Question::options, (first, second) -> first,
                        LinkedHashMap::new)));
    }
}
