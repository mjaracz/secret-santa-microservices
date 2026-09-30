package com.secretsanta.common.group.events;

import com.secretsanta.common.BaseEvent;
import com.secretsanta.common.group.dto.GroupSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MyGroupsFetchedEvent extends BaseEvent {

	private List<GroupSummaryDto> groups;
}
