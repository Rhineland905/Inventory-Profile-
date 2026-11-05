# Trade Roller (Fabric, Minecraft 26.3)

Client mod that rerolls a villager's trades for you. It breaks the workstation (lectern),
waits until the villager loses its job, puts the workstation back, opens the trades and
checks them. It repeats until the trade you want appears, then stops with the trade screen open.

- **U**: settings. Type what you want, comma separated, any one is enough:
  `mending, efficiency 5, unbreaking 3` (level can be a number or I..V). An item id such as
  `diamond_pickaxe` also works. You can also set the highest emerald price.
- **J**: look at the workstation next to the villager and press J to start; J again stops.

Stand right next to the workstation so you pick it back up (or keep a spare in the hotbar).
Never trade with the villager before you find the trade: after the first trade its offers are locked.
Works in singleplayer and on servers, but check the server rules: many ban automation.
Requires Fabric API.
