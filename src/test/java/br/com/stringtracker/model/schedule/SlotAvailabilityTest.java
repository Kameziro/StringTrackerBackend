package br.com.stringtracker.model.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotAvailabilityTest {

    private static LessonSlot slot(int capacity, LessonSlotStatus status) {
        LessonSlot slot = new LessonSlot();
        slot.setCapacity((short) capacity);
        slot.setStatus(status);
        return slot;
    }

    @Test
    void privateSlot_isFreeHeldOrBookedByItsOnlySeat() {
        LessonSlot slot = slot(1, LessonSlotStatus.OPEN);

        assertEquals(SlotAvailability.FREE, SlotAvailability.of(slot, 0, 0));
        assertEquals(SlotAvailability.HELD, SlotAvailability.of(slot, 1, 0));
        assertEquals(SlotAvailability.BOOKED, SlotAvailability.of(slot, 0, 1));
    }

    @Test
    void groupSlot_staysFreeWhileSeatsRemain_andFillsUpAsBookedOnlyWhenSomeSeatIsConfirmed() {
        LessonSlot slot = slot(4, LessonSlotStatus.OPEN);

        assertEquals(SlotAvailability.FREE, SlotAvailability.of(slot, 1, 2));
        assertEquals(SlotAvailability.BOOKED, SlotAvailability.of(slot, 1, 3));
        assertEquals(SlotAvailability.HELD, SlotAvailability.of(slot, 4, 0));
    }

    @Test
    void blockedSlot_isBlockedEvenWithBookings() {
        assertEquals(SlotAvailability.BLOCKED, SlotAvailability.of(slot(1, LessonSlotStatus.BLOCKED), 0, 1));
    }
}
