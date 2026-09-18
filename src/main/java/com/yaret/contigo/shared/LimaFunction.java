package com.yaret.contigo.shared;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/** Lima is UTC-05 without daylight saving; the SQL expression only changes grouping. */
public class LimaFunction implements FunctionContributor {
  @Override
  public void contributeFunctions(FunctionContributions contributions) {
    contributions
        .getFunctionRegistry()
        .registerPattern(
            "contigo_lima",
            "dateadd(hour,-5,?1)",
            contributions
                .getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.INSTANT));
  }
}
