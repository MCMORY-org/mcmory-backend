package com.mcmory.backend.store;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store")
public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal lat;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal lng;

	@Column(name = "open_time", nullable = false)
	private LocalTime openTime;

	@Column(name = "close_time", nullable = false)
	private LocalTime closeTime;

	@Column(name = "repair_available", nullable = false)
	private boolean repairAvailable;

	protected Store() {
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getAddress() {
		return this.address;
	}

	public LocalTime getOpenTime() {
		return this.openTime;
	}

	public LocalTime getCloseTime() {
		return this.closeTime;
	}

	public boolean isRepairAvailable() {
		return this.repairAvailable;
	}

}
