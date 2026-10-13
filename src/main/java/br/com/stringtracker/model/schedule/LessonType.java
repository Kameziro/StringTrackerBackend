package br.com.stringtracker.model.schedule;

public enum LessonType {
    SINGLES,
    DOUBLES,
    GROUP;

    /** Aula particular aceita singles ou duplas; turma só aceita grupo. */
    public boolean fits(LessonKind kind) {
        return (this == GROUP) == (kind == LessonKind.GROUP);
    }
}
