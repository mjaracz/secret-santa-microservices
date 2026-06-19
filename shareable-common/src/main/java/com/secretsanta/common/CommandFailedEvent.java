package com.secretsanta.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommandFailedEvent extends BaseEvent {

	private String reason;
	private String originalCommandType;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String errorCode;
}
