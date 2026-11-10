
package com.example.elsa_store.entity;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(name = "code", length = 50, unique = true)
    private String code;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "address_id")
    private Address address;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User user;

    /** Cửa hàng bán đơn này. */
    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    /** Nhân viên / quản lý lập đơn tại quầy; null khi khách tự đặt. */
    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "order_date")
    private LocalDateTime orderDate;

    /**
     * Lúc đơn chuyển sang HOAN_THANH — doanh thu được ghi nhận vào ngày này. Đơn hoàn thành trước khi có cột này để
     * trống, báo cáo dùng ngày đặt thay thế.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Lúc nhắc khách xác nhận đã nhận hàng (trước khi đơn tự hoàn thành); null = chưa nhắc. */
    @Column(name = "delivery_reminded_at")
    private LocalDateTime deliveryRemindedAt;

    /** Lưu dạng chuỗi (không dùng kiểu enum của MySQL) để thêm trạng thái mới không phải sửa cột. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 30)
    private OrderStatus status;

    /** Lý do huỷ (khách hoặc cửa hàng nhập khi huỷ); trống với đơn chưa huỷ hoặc huỷ trước khi có cột này. */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** Đơn vị vận chuyển (GHN, GHTK…) cửa hàng nhập khi giao; null = chưa nhập hoặc cửa hàng tự giao không ghi. */
    @Column(name = "shipping_carrier", length = 50)
    private String shippingCarrier;

    /** Mã vận đơn để khách tra cứu ở đơn vị vận chuyển; null = chưa nhập. */
    @Column(name = "tracking_code", length = 50)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "final_amount")
    private Double finalAmount;

    @Column(name = "note", length = 255)
    private String note;

    // Thông tin giao hàng chụp lại lúc đặt (khách sửa / xoá sổ địa chỉ sau đó cũng không đổi đơn cũ).
    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    /**
     * Phí giao hàng đã tính vào {@code finalAmount}; 0 khi được miễn phí, trống ở đơn tại quầy và đơn cũ. Khách trả
     * {@code finalAmount = totalAmount − discountAmount + shippingFee}.
     */
    @Column(name = "shipping_fee")
    private Double shippingFee;

    /** COD hoặc VNPAY — cách khách chọn trả tiền lúc đặt. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /** Mã giảm giá đã áp cho đơn (nếu có). */
    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    /** Xu khách dùng để giảm giá đơn (1 xu = 1đ, đã tính vào {@code discountAmount}); null = không dùng. */
    @Column(name = "coins_used")
    private Integer coinsUsed;

    /** Xu khách nhận khi đơn hoàn thành; null = chưa cộng (đơn chưa xong hoặc không phải tài khoản khách). */
    @Column(name = "coins_earned")
    private Integer coinsEarned;

    /** Mô tả các khoản giảm, ví dụ "Giảm giá sản phẩm 50.000đ; Mã SALE10 -20.000đ". */
    @Column(name = "discount_note", length = 255)
    private String discountNote;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<Payment> payments;
}
