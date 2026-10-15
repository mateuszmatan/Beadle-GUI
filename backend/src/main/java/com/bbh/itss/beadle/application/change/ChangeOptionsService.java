package com.bbh.itss.beadle.application.change;

import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.change.port.in.ChangeOptions;
import com.bbh.itss.beadle.application.change.port.in.ChangeOptions.TypeOption;
import com.bbh.itss.beadle.application.change.port.in.ChangeOptionsUseCase;
import com.bbh.itss.beadle.domain.change.ChangeTemplate.Type;
import com.bbh.itss.beadle.domain.change.RiskAssessment.Question;

import java.util.LinkedHashMap;

import static com.bbh.itss.beadle.domain.change.ChangeTemplate.CATEGORIES;
import static com.bbh.itss.beadle.domain.change.TaskDetails.IMPORTANCES;
import static com.bbh.itss.beadle.domain.change.TaskDetails.PLATFORMS;
import static com.bbh.itss.beadle.domain.change.TaskDetails.RELEASE_MANAGEMENT;
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
                        LinkedHashMap::new)), PLATFORMS, IMPORTANCES, RELEASE_MANAGEMENT);
    }
}
