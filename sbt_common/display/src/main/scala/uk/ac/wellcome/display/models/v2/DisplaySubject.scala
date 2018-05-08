package uk.ac.wellcome.display.models.v2

import com.fasterxml.jackson.annotation.JsonProperty
import uk.ac.wellcome.models.work.internal.{
  AbstractConcept,
  Displayable,
  Subject
}

case class DisplaySubject(label: String,
                          concepts: List[DisplayAbstractConcept],
                          @JsonProperty("type") ontologyType: String =
                            "Subject")

object DisplaySubject {
  def apply(subject: Subject[Displayable[AbstractConcept]]): DisplaySubject =
    DisplaySubject(label = subject.label, concepts = subject.concepts.map {
      DisplayAbstractConcept(_)
    }, ontologyType = subject.ontologyType)
}
