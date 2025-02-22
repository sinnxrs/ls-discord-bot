package com.learnspigot.bot.help

import com.learnspigot.bot.profile.ProfileRegistry
import com.learnspigot.bot.Server
import com.learnspigot.bot.util.embed
import gg.flyte.neptune.annotation.Command
import gg.flyte.neptune.annotation.Inject
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.ThreadMember
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu

class CloseCommand {

    @Inject
    private lateinit var profileRegistry: ProfileRegistry

    @Command(
        name = "close", description = "Close a help post"
    )
    fun onCloseCommand(event: SlashCommandInteractionEvent) {
        if (!event.isFromGuild) return
        if (event.channelType != ChannelType.GUILD_PUBLIC_THREAD) return

        val channel = event.guildChannel.asThreadChannel()
        if (channel.parentChannel.id != Server.helpChannel.id) return

        if (event.member!!.id != channel.ownerId && !event.member!!.roles.contains(Server.managementRole)) {
            event.reply("You cannot close this thread!").setEphemeral(true).queue()
            return
        }

        val contributors: List<Member> =
            channel.retrieveThreadMembers().complete().filter { member: ThreadMember -> member.id != channel.ownerId }
                .filter { member: ThreadMember -> !member.user.isBot }.take(25).map { it.member }

        if (contributors.isEmpty()) {
            event.replyEmbeds(
                embed().setTitle(event.member!!.effectiveName + " has closed the thread")
                    .setDescription("Listing no contributors.").build()
            ).complete()
            channel.manager.setArchived(true).setLocked(true).complete()
            return
        }

        channel.sendMessage(channel.owner!!.asMention).queue { message ->
            message.delete().queue()
        }

        event.deferReply().queue()

        event.hook.sendMessageEmbeds(
            embed().setTitle("Who helped you solve your issue?").setDescription(
                """
                                Please select people from the dropdown who helped solve your issue.
                                                                
                                Once you've selected contributors, click the Close button to close your post.
                    """
            ).build()
        ).addActionRow(
            StringSelectMenu.create(channel.id + "-contributor-selector")
                .setPlaceholder("Select the people that helped solve your issue").setRequiredRange(0, 25)
                .addOptions(contributors.map { member: Member ->
                    SelectOption.of(
                        member.effectiveName, member.id
                    ).withDescription(member.user.name)
                }).build()
        ).addActionRow(Button.danger(channel.id + "-close-button", "Close"))
            .queue { message: Message -> profileRegistry.messagesToRemove[event.channel.id] = message }
    }
}