package br.com.stringtracker.model.schedule;

import br.com.stringtracker.model.BaseEntity;
import br.com.stringtracker.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Reserva de uma vaga (seat) de um {@link LessonSlot}. Feita por um aluno com conta
 * ({@code studentUser}) ou por um convidado sem conta ({@code guestName}), nunca sem um dos dois.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_slot_id", nullable = false)
    private LessonSlot lessonSlot;

    @Column(nullable = false)
    private short seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id")
    private User studentUser;

    @Column(name = "guest_name", length = 120)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "partner_name", length = 120)
    private String partnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "lesson_type", nullable = false, length = 8)
    private LessonType lessonType;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 8)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 8)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "refund_amount_cents")
    private Long refundAmountCents;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    public static Booking create(LessonSlot lessonSlot, short seat, LessonType lessonType, long priceCents,
                                 PaymentMode paymentMode, BookingStatus status, User createdBy) {
        Booking booking = new Booking();
        booking.lessonSlot = lessonSlot;
        booking.seat = seat;
        booking.lessonType = lessonType;
        booking.priceCents = priceCents;
        booking.paymentMode = paymentMode;
        booking.status = status;
        booking.createdBy = createdBy;
        return booking;
    }
}
