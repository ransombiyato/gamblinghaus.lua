-- =========================
-- moni setuper 5000
-- =========================

local money = 250

print("you start with", money, "dollars")

-- =========================
-- upgrade variablz
-- these stay betwin gems ykwin?
-- =========================

local playermincard = 2
local dealermaxcard = 11
local emergencymoni = 25
local winmultiplier = 0.5

local playermincardupgrades = 0
local dealermaxcardupgrades = 0
local emergencymoniupgrades = 0
local winmultiplierupgrades = 0

-- =========================
-- main game looping the rooms kurikaesu furakotaru
-- restarts everi raund dont forget it ribi
-- =========================

while true do

    -- =========================
    -- starting hands
    -- =========================

    local yourstartingcards =
        math.random(playermincard,11) +
        math.random(playermincard,11)

    local dealerstartingcards =
        math.random(2,dealermaxcard) +
        math.random(2,dealermaxcard)

    -- =========================
    -- a s k i n g to play
    -- =========================

    io.write("play blackjack?(its the only game we got rn we broke) ")
    local yesorno = tostring(io.read())

    while true do
        if yesorno == "yes" then
            print("game started")
            break

        elseif yesorno == "no" then
            print("gtfo bru we only accept yeses")
            return

        else
            print("THATS NOT YES OR NO, I ASKED YOU YES OR NO.")
            io.write("play blackjack? ")
            yesorno = tostring(io.read())
        end
    end

    -- =========================
    -- betting schism
    -- =========================

    print("current moni:", money)

    io.write("how much moni do you bet? ")
    local bet = tonumber(io.read())

    while bet > money or bet < 25 do
        print("minimum bet is 25 and you cant bet more than your moni")
        io.write("how much moni do you bet? ")
        bet = tonumber(io.read())
    end

    -- =========================
    -- looping the rooms kurikaesu furakotaru
    -- =========================

    while true do

        print("")
        print("you have", yourstartingcards, "card values in total")
        print("dealer has", dealerstartingcards, "card values in total")
        print("current moni:", money)

        -- =========================
        -- player busted checkr
        -- =========================

        if yourstartingcards > 21 then

            print("you busted it down gambling style")
            print("dealer winga!!!!")

            money = money - bet

            print("you lost", bet, "moni")

            break
        end

        -- =========================
        -- players turn
        -- =========================

        io.write("what do you do? hit or stand? ")
        local yourpick = tostring(io.read())

        if yourpick == "hit" then

            local card =
                math.random(playermincard,11)

            print("you drew a", card)

            yourstartingcards =
                yourstartingcards + card

        elseif yourpick == "stand" then

            print("you stand")

            -- =========================
            -- dealers turn
            -- =========================

            while dealerstartingcards < 17 do

                local dealercard =
                    math.random(2,dealermaxcard)

                print("dealer drew a", dealercard)

                dealerstartingcards =
                    dealerstartingcards + dealercard

            end

            -- =========================
            -- win / lose checkr
            -- =========================

            print("")
            print("FINAL RESULTS")
            print("you:", yourstartingcards)
            print("dealer:", dealerstartingcards)

            if dealerstartingcards > 21 then

                print("dealer busts it down gambling style")
                print("you winga!!!!")

                money =
                    money +
                    math.floor(bet * winmultiplier)

                print(
                    "you gained",
                    math.floor(bet * winmultiplier),
                    "moni"
                )

            elseif yourstartingcards > dealerstartingcards then

                print("you winga!!!!")

                money =
                    money +
                    math.floor(bet * winmultiplier)

                print(
                    "you gained",
                    math.floor(bet * winmultiplier),
                    "moni"
                )

            elseif dealerstartingcards > yourstartingcards then

                print("dealer winga!!!!")

                money = money - bet

                print("you lost", bet, "moni")

            else

                print("drawwwww")

            end

            break

        else

            print("nawt hit or stand brosk")

        end

    end

    -- =========================
    -- the casinos mercy
    -- =========================

    if money <= 0 then

        money = emergencymoni

        print("")
        print("the casino pities your broke ahh")
        print("you got", emergencymoni, "emergency moni from the casino")

    end

    print("")
    print("you now have", money, "moni")
    
-- =========================
-- UPGRADE SHOP
-- =========================

io.write("go to de upgradez haus? yes/no ")
local upgrades = tostring(io.read())

if upgrades == "yes" then

    print("1. the cardschisminator")
print("raises your minimum card roll by 1")
print("cost: 100")
print("owned:", playermincardupgrades, "/3")
print("")

print("2. dealer nerfer")
print("lowers dealer max card roll by 1")
print("cost: 150")
print("owned:", dealermaxcardupgrades, "/2")
print("")

print("3. lucky backpocket")
print("gives +25 more emergency moni when broke")
print("cost: 250")
print("owned:", emergencymoniupgrades, "/2")
print("")

print("4. casino magnet")
print("increases winnings by 10 percent")
print("cost: 500")
print("owned:", winmultiplierupgrades, "/2")
print("")

print("5. RETURN TO GAMBLING")

    io.write("pick upgrade number: ")
    local choice = tostring(io.read())

    if choice == "5" then

        print("returning to le gamble haus")

    elseif choice == "1" then

        if money >= 125 and playermincardupgrades < 3 then

            money = money - 100
            playermincard = playermincard + 1
            playermincardupgrades =
                playermincardupgrades + 1

            print("upgrade bought")

        else

            print("cant buy that")

        end

    elseif choice == "2" then

        if money >= 175 and dealermaxcardupgrades < 2 then

            money = money - 150
            dealermaxcard = dealermaxcard - 1
            dealermaxcardupgrades =
                dealermaxcardupgrades + 1

            print("upgrade bought")

        else

            print("cant buy that")

        end

    elseif choice == "3" then

        if money >= 275 and emergencymoniupgrades < 2 then

            money = money - 250
            emergencymoni =
                emergencymoni + 25

            emergencymoniupgrades =
                emergencymoniupgrades + 1

            print("upgrade bought")

        else

            print("cant buy that")

        end

    elseif choice == "4" then

        if money >= 525 and winmultiplierupgrades < 2 then

            money = money - 500
            winmultiplier =
                winmultiplier + 0.1

            winmultiplierupgrades =
                winmultiplierupgrades + 1

            print("upgrade bought")

        else

            print("cant buy that")

        end

    else

        print("bro that aint a valid upgrade")

    end

end

    -- =========================
    -- replay part
    -- =========================

    print("")
    print("current moni:", money)

    io.write("play again? yes/no ")
    local again = tostring(io.read())

    if again ~= "yes" then
        print("cya at le gamble haus")
        break
    end

end
