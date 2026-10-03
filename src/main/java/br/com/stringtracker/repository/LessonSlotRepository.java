package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.LessonSlot;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LessonSlotRepository implements PanacheRepository<LessonSlot> {
}
