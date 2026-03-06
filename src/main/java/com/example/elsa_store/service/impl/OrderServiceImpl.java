
package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.OrderStatus;
import com.example.elsa_store.constant.PaymentStatus;
import com.example.elsa_store.dto.request.OrderRequest;
import com.example.elsa_store.dto.response.OrderResponse;
import com.example.elsa_store.entity.*;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.*;
import com.example.elsa_store.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public OrderServiceImpl(OrderRepository orderRepository,
                            CustomerRepository customerRepository,
                            AddressRepository addressRepository,
                            ProductVariantRepository productVariantRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        order.setCode("ORD-" + System.currentTimeMillis());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CHUA_XAC_NHAN);

        if (request.getPaymentMethod() == 0) {
            order.setPaymentStatus(PaymentStatus.CHUA_THANH_TOAN);
        } else {
            order.setPaymentStatus(PaymentStatus.CHO_THANH_TOAN);
        }

        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            order.setUser(user);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            order.setCustomer(customer);
        }

        if (request.getAddressId() != null) {
            Address address = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
            order.setAddress(address);
        }

        List<OrderItem> items = new ArrayList<>();
        Double total = 0.0;

        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemReq.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductVariant(variant);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(variant.getPrice() != null ? variant.getPrice() : 0.0);
            Double lineTotal = item.getUnitPrice() * item.getQuantity();
            item.setLineTotal(lineTotal);
            total += lineTotal;
            items.add(item);
        }

        order.setItems(items);
        order.setTotalAmount(total);
        order.setFinalAmount(total);

        order = orderRepository.save(order);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OrderResponse> getAllByUser(Long userId) {
        return orderRepository.findAllByUser_Id(userId).stream()
                .map(this::toResponse)
                .toList();
    }


    @Override
    public void delete(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order not found");
        }
        orderRepository.deleteById(id);
    }

    @Override
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        PaymentStatus paymentStatus = order.getPaymentStatus();

        if (status == OrderStatus.HOAN_THANH
                && paymentStatus != PaymentStatus.DA_THANH_TOAN
                && paymentStatus != PaymentStatus.CHUA_THANH_TOAN) {
            throw new RuntimeException("Không thể hoàn thành khi chưa thanh toán");
        }

        if (status == OrderStatus.DA_HUY
                && paymentStatus == PaymentStatus.DA_THANH_TOAN) {
            throw new RuntimeException("Không thể hủy đơn đã thanh toán");
        }

        order.setStatus(status);
        return toResponse(orderRepository.save(order));
    }

    @Override
    public OrderResponse updatePaymentStatus(Long orderId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setPaymentStatus(paymentStatus);

        return toResponse(orderRepository.save(order));
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setCode(order.getCode());
        res.setOrderDate(order.getOrderDate());
        res.setTotalAmount(order.getTotalAmount());
        res.setFinalAmount(order.getFinalAmount());
        res.setStatus(order.getStatus());
        res.setPaymentStatus(order.getPaymentStatus());

        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                OrderResponse.OrderItemResponse ir = new OrderResponse.OrderItemResponse();
                ir.setProductVariantId(item.getProductVariant().getId());
                ir.setProductName(item.getProductVariant().getProduct().getName());
                ir.setPathImage(item.getProductVariant().getImageUrl());
                ir.setQuantity(item.getQuantity());
                ir.setUnitPrice(item.getUnitPrice());
                ir.setLineTotal(item.getLineTotal());
                itemResponses.add(ir);
            }
        }
        res.setItems(itemResponses);
        return res;
    }
}
