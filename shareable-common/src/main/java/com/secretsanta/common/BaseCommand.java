package com.secretsanta.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.secretsanta.common.group.commands.AddMemberCommand;
import com.secretsanta.common.group.commands.CreateGroupCommand;
import com.secretsanta.common.group.commands.DeleteGroupCommand;
import com.secretsanta.common.group.commands.DrawNamesCommand;
import com.secretsanta.common.group.commands.UpdateGroupCommand;
import com.secretsanta.common.user.commands.CreateUserCommand;
import com.secretsanta.common.user.commands.DeleteUserCommand;
import com.secretsanta.common.user.commands.AuthenticateUserCommand;
import com.secretsanta.common.user.commands.RefreshSessionCommand;
import com.secretsanta.common.user.commands.ResendEmailVerificationCommand;
import com.secretsanta.common.user.commands.RevokeSessionCommand;
import com.secretsanta.common.user.commands.UpdateUserCommand;
import com.secretsanta.common.user.commands.VerifyEmailCommand;
import com.secretsanta.common.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "commandType",
		visible = true
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = CreateUserCommand.class, name = "CREATE_USER"),
		@JsonSubTypes.Type(value = AuthenticateUserCommand.class, name = "AUTHENTICATE_USER"),
		@JsonSubTypes.Type(value = RefreshSessionCommand.class, name = "REFRESH_SESSION"),
		@JsonSubTypes.Type(value = RevokeSessionCommand.class, name = "REVOKE_SESSION"),
		@JsonSubTypes.Type(value = VerifyEmailCommand.class, name = "VERIFY_EMAIL"),
		@JsonSubTypes.Type(value = ResendEmailVerificationCommand.class, name = "RESEND_EMAIL_VERIFICATION"),
		@JsonSubTypes.Type(value = UpdateUserCommand.class, name = "UPDATE_USER"),
		@JsonSubTypes.Type(value = DeleteUserCommand.class, name = "DELETE_USER"),
		@JsonSubTypes.Type(value = CreateGroupCommand.class, name = "CREATE_GROUP"),
		@JsonSubTypes.Type(value = UpdateGroupCommand.class, name = "UPDATE_GROUP"),
		@JsonSubTypes.Type(value = DeleteGroupCommand.class, name = "DELETE_GROUP"),
		@JsonSubTypes.Type(value = AddMemberCommand.class, name = "ADD_MEMBER"),
		@JsonSubTypes.Type(value = DrawNamesCommand.class, name = "DRAW_NAMES")
})
public abstract class BaseCommand {

	@JsonProperty("commandId")
	private String commandId;

	@JsonProperty("timestamp")
	private long timestamp;

	@JsonProperty("commandType")
	private String commandType;

	@JsonProperty("actorId")
	private String actorId;

	@JsonProperty("actorRoles")
	private Set<UserRole> actorRoles;

	public void initDefaults(String type) {
		if (this.commandId == null) {
			this.commandId = UUID.randomUUID().toString();
		}
		if (this.timestamp == 0) {
			this.timestamp = Instant.now().toEpochMilli();
		}
		this.commandType = type;
	}
}
